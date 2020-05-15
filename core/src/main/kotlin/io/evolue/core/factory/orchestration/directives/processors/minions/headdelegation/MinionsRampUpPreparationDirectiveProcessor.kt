package io.evolue.core.factory.orchestration.directives.processors.minions.headdelegation

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveProducer
import io.evolue.core.cross.driving.directives.MinionStartDefinition
import io.evolue.core.cross.driving.directives.MinionsRampUpPreparationDirective
import io.evolue.core.cross.driving.directives.MinionsStartDirective
import io.evolue.core.cross.driving.feedback.DirectiveFeedback
import io.evolue.core.cross.driving.feedback.FeedbackProducer
import io.evolue.core.cross.driving.feedback.FeedbackStatus
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import java.util.LinkedList

/**
 *
 * The [MinionsRampUpPreparationDirectiveProcessor] is responsible for generating the ramp-up strategy to start all
 * the [io.evolue.api.orchestration.Minion]s for the execution of a scenario.
 *
 * @author Eric Jessé
 */
internal class MinionsRampUpPreparationDirectiveProcessor(
    private val scenariosKeeper: ScenariosKeeper,
    private val directiveProducer: DirectiveProducer,
    private val feedbackProducer: FeedbackProducer,
    private val minionsCreationPreparationDirectiveProcessor: MinionsCreationPreparationDirectiveProcessor
) : DirectiveProcessor<MinionsRampUpPreparationDirective> {

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
                val rampUpStrategyIterator = scenario.rampUpStrategy.iterator(createdMinions.size)
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