package io.qalipsis.core.factories.orchestration.directives.processors.minions.headdelegation

import cool.graph.cuid.Cuid
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.core.cross.directives.MinionsCreationDirective
import io.qalipsis.core.cross.directives.MinionsCreationPreparationDirectiveReference
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.core.factories.orchestration.ScenariosKeeper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

/**
 *
 * The [MinionsCreationPreparationDirectiveProcessor] is responsible for generating the identifiers for the minions to
 * create for each scenario and DAG.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinionsCreationPreparationDirectiveProcessor(
    private val scenariosKeeper: ScenariosKeeper,
    private val directiveRegistry: DirectiveRegistry,
    private val directiveProducer: DirectiveProducer,
    private val feedbackProducer: FeedbackProducer
) : DirectiveProcessor<MinionsCreationPreparationDirectiveReference> {

    val minions: MutableMap<ScenarioId, List<MinionId>> = ConcurrentHashMap()

    @LogInputAndOutput
    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsCreationPreparationDirectiveReference) {
            return scenariosKeeper.hasScenario(directive.scenarioId)
        }
        return false
    }

    override suspend fun process(directive: MinionsCreationPreparationDirectiveReference) {
        scenariosKeeper.getScenario(directive.scenarioId)?.let { scenario ->
            directiveRegistry.read(directive)?.let { minionsCount ->
                log.debug("Creating $minionsCount minions IDs for the scenario ${directive.scenarioId}")
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
                    val minionsCreationDirective =
                        MinionsCreationDirective(directive.campaignId, scenario.id, dag.id, minions)
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
