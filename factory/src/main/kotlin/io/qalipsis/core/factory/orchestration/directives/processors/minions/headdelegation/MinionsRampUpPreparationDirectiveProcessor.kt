package io.qalipsis.core.factory.orchestration.directives.processors.minions.headdelegation

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.ScenariosRegistry
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import jakarta.inject.Singleton
import org.slf4j.event.Level
import java.util.LinkedList

/**
 *
 * The [MinionsRampUpPreparationDirectiveProcessor] is responsible for generating the ramp-up strategy to start all
 * the [io.qalipsis.api.orchestration.Minion]s for the execution of a scenario.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinionsRampUpPreparationDirectiveProcessor(
    private val scenariosRegistry: ScenariosRegistry,
    private val directiveProducer: DirectiveProducer,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val minionsCreationPreparationDirectiveProcessor: MinionsCreationPreparationDirectiveProcessor,
    private val factoryConfiguration: FactoryConfiguration,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<MinionsRampUpPreparationDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsRampUpPreparationDirective) {
            // The scenario has to be known and the same factory should have generated all the minions IDs.
            return directive.scenarioId in scenariosRegistry
                    && minionsCreationPreparationDirectiveProcessor.minions.containsKey(directive.scenarioId)
        }
        return false
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: MinionsRampUpPreparationDirective) {
        scenariosRegistry[directive.scenarioId]?.let { scenario ->
            try {
                feedbackFactoryChannel.publish(
                    DirectiveFeedback(directiveKey = directive.key, status = FeedbackStatus.IN_PROGRESS)
                )

                val createdMinions =
                    LinkedList(minionsCreationPreparationDirectiveProcessor.minions[directive.scenarioId])
                val rampUpStrategyIterator =
                    scenario.rampUpStrategy.iterator(createdMinions.size, directive.speedFactor)
                var start = System.currentTimeMillis() + directive.startOffsetMs
                val minionsStartDefinitions = mutableListOf<MinionStartDefinition>()

                log.debug { "Creating the ramp-up for ${createdMinions.size} minions on campaign ${directive.campaignId} of scenario ${scenario.id}" }
                while (createdMinions.isNotEmpty()) {
                    val nextStartingLine = rampUpStrategyIterator.next()
                    require(nextStartingLine.count > 0) { "nextStartingLine.count <= 0" }
                    require(nextStartingLine.offsetMs > 0) { "nextStartingLine.offsetMs <= 0" }
                    start += nextStartingLine.offsetMs

                    for (i in 0 until nextStartingLine.count.coerceAtMost(createdMinions.size)) {
                        minionsStartDefinitions.add(MinionStartDefinition(createdMinions.removeFirst(), start))
                    }
                }
                log.debug { "Ramp-up creation is complete on campaign ${directive.campaignId} of scenario ${scenario.id}" }
                directiveProducer.publish(MinionsStartDirective(
                    scenario.id,
                    minionsStartDefinitions,
                    channel = factoryConfiguration.directiveRegistry.broadcastDirectivesChannel,
                    key = idGenerator.short()))

                feedbackFactoryChannel.publish(
                    DirectiveFeedback(directiveKey = directive.key, status = FeedbackStatus.COMPLETED)
                )
            } catch (e: Exception) {
                log.error(e) { "Error when processing $directive: ${e.message}" }
                feedbackFactoryChannel.publish(
                    DirectiveFeedback(
                        directiveKey = directive.key, status = FeedbackStatus.FAILED,
                        error = e.message
                    )
                )

                throw e
            }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}