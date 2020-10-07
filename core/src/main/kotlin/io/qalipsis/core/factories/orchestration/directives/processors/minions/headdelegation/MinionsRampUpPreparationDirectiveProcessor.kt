package io.qalipsis.core.factories.orchestration.directives.processors.minions.headdelegation

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.core.cross.directives.MinionStartDefinition
import io.qalipsis.core.cross.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.cross.directives.MinionsStartDirective
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.core.factories.orchestration.ScenariosKeeper
import java.util.LinkedList
import javax.inject.Singleton

/**
 *
 * The [MinionsRampUpPreparationDirectiveProcessor] is responsible for generating the ramp-up strategy to start all
 * the [io.qalipsis.api.orchestration.Minion]s for the execution of a scenario.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinionsRampUpPreparationDirectiveProcessor(
    private val scenariosKeeper: ScenariosKeeper,
    private val directiveProducer: DirectiveProducer,
    private val feedbackProducer: FeedbackProducer,
    private val minionsCreationPreparationDirectiveProcessor: MinionsCreationPreparationDirectiveProcessor
) : DirectiveProcessor<MinionsRampUpPreparationDirective> {

    @LogInputAndOutput
    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsRampUpPreparationDirective) {
            // The scenario has to be known and the same factory should have generated all the minions IDs.
            return scenariosKeeper.hasScenario(directive.scenarioId)
                    && minionsCreationPreparationDirectiveProcessor.minions.containsKey(directive.scenarioId)
        }
        return false
    }

    override suspend fun process(directive: MinionsRampUpPreparationDirective) {
        scenariosKeeper.getScenario(directive.scenarioId)?.let { scenario ->
            try {
                feedbackProducer.publish(
                    DirectiveFeedback(directiveKey = directive.key, status = FeedbackStatus.IN_PROGRESS)
                )

                val createdMinions =
                    LinkedList(minionsCreationPreparationDirectiveProcessor.minions[directive.scenarioId])
                val rampUpStrategyIterator =
                    scenario.rampUpStrategy.iterator(createdMinions.size, directive.speedFactor)
                var start = System.currentTimeMillis() + directive.startOffsetMs
                val minionsStartDefinitions = mutableListOf<MinionStartDefinition>()

                while (createdMinions.isNotEmpty()) {
                    val nextStartingLine = rampUpStrategyIterator.next()
                    require(nextStartingLine.count > 0) { "nextStartingLine.count <= 0" }
                    require(nextStartingLine.offsetMs > 0) { "nextStartingLine.offsetMs <= 0" }
                    start += nextStartingLine.offsetMs

                    for (i in 0 until nextStartingLine.count.coerceAtMost(createdMinions.size)) {
                        minionsStartDefinitions.add(MinionStartDefinition(createdMinions.removeFirst(), start))
                    }
                }
                directiveProducer.publish(MinionsStartDirective(scenario.id, minionsStartDefinitions))

                feedbackProducer.publish(
                    DirectiveFeedback(directiveKey = directive.key, status = FeedbackStatus.COMPLETED)
                )
            } catch (e: Exception) {
                log.error("Error when processing $directive: ${e.message}", e)
                feedbackProducer.publish(
                    DirectiveFeedback(directiveKey = directive.key, status = FeedbackStatus.FAILED, error = e.message)
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
