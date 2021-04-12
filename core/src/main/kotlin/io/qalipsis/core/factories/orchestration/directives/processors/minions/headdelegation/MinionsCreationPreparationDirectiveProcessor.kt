package io.qalipsis.core.factories.orchestration.directives.processors.minions.headdelegation

import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.directives.MinionsCreationDirective
import io.qalipsis.core.cross.directives.MinionsCreationPreparationDirectiveReference
import io.qalipsis.core.factories.orchestration.ScenariosRegistry
import org.slf4j.event.Level
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
    private val scenariosRegistry: ScenariosRegistry,
    private val directiveRegistry: DirectiveRegistry,
    private val directiveProducer: DirectiveProducer,
    private val feedbackProducer: FeedbackProducer,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<MinionsCreationPreparationDirectiveReference> {

    val minions: MutableMap<ScenarioId, List<MinionId>> = ConcurrentHashMap()

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsCreationPreparationDirectiveReference) {
            return directive.scenarioId in scenariosRegistry
        }
        return false
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: MinionsCreationPreparationDirectiveReference) {
        scenariosRegistry[directive.scenarioId]?.let { scenario ->
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
                    if (dag.isSingleton || !dag.isUnderLoad) {
                        // DAGs not under load receive a unique minion.
                        minions.add(scenario.id + "-singleton-" + idGenerator.short())
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
        return scenarioId + "-" + idGenerator.short()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
