package io.evolue.core.factory.orchestration.directives.processors.minions.headdelegation

import cool.graph.cuid.Cuid
import io.evolue.api.context.MinionId
import io.evolue.api.context.ScenarioId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveProducer
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.core.cross.driving.directives.MinionsCreationDirective
import io.evolue.core.cross.driving.directives.MinionsCreationPreparationDirectiveReference
import io.evolue.core.cross.driving.feedback.DirectiveFeedback
import io.evolue.core.cross.driving.feedback.FeedbackProducer
import io.evolue.core.cross.driving.feedback.FeedbackStatus
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * The [MinionsCreationPreparationDirectiveProcessor] is responsible for generating the identifiers for the minions to
 * create for each scenario and DAG.
 *
 * @author Eric Jessé
 */
internal class MinionsCreationPreparationDirectiveProcessor(
    private val scenariosKeeper: ScenariosKeeper,
    private val directiveRegistry: DirectiveRegistry,
    private val directiveProducer: DirectiveProducer,
    private val feedbackProducer: FeedbackProducer
) : DirectiveProcessor<MinionsCreationPreparationDirectiveReference> {

    val minions: MutableMap<ScenarioId, List<MinionId>> = ConcurrentHashMap()

    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsCreationPreparationDirectiveReference) {
            return scenariosKeeper.hasScenario(directive.scenarioId)
        }
        return false
    }

    override suspend fun process(directive: MinionsCreationPreparationDirectiveReference) {
        scenariosKeeper.getScenario(directive.scenarioId)?.let { scenario ->
            directiveRegistry.read(directive)?.let { minionsCount ->
                log.debug("Creating $minionsCount minions for the scenario ${directive.scenarioId}")
                feedbackProducer.publish(
                    DirectiveFeedback(directiveKey = directive.key, status = FeedbackStatus.IN_PROGRESS)
                )
                val allMinions = mutableListOf<MinionId>()
                for (i in 0 until minionsCount) {
                    allMinions.add(generateMinionId(scenario.id))
                }
                minions[scenario.id] = allMinions

                scenario.dags.forEach { dag ->
                    val minions = mutableListOf<MinionId>()
                    if (dag.singleton) {
                        // Singleton DAGs receive their own minion each.
                        minions.add(scenario.id + "-singleton-" + Cuid.createCuid())
                    } else {
                        minions.addAll(allMinions)
                    }
                    val minionsCreationDirective = MinionsCreationDirective(scenario.id, dag.id, minions)
                    directiveProducer.publish(minionsCreationDirective)
                }
                feedbackProducer.publish(
                    DirectiveFeedback(directiveKey = directive.key, status = FeedbackStatus.COMPLETED)
                )
            }
        }
    }

    private fun generateMinionId(scenarioId: ScenarioId): MinionId {
        return scenarioId + "-" + Cuid.createCuid()
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}