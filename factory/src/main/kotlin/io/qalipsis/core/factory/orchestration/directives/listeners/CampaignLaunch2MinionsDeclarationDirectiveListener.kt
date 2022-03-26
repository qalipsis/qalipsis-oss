package io.qalipsis.core.factory.orchestration.directives.listeners

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.directives.MinionsDeclarationDirectiveReference
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * The [CampaignLaunch2MinionsDeclarationDirectiveListener] is responsible for generating the identifiers for the minions to
 * create for each scenario and DAG.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch2MinionsDeclarationDirectiveListener(
    private val scenarioRegistry: ScenarioRegistry,
    private val factoryChannel: FactoryChannel,
    private val idGenerator: IdGenerator,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryCampaignManager: FactoryCampaignManager
) : DirectiveListener<MinionsDeclarationDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsDeclarationDirectiveReference
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId, directive.scenarioId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsDeclarationDirective) {
        val feedback = MinionsDeclarationFeedback(
            campaignId = directive.campaignId,
            scenarioId = directive.scenarioId,
            status = FeedbackStatus.IN_PROGRESS
        )
        factoryChannel.publishFeedback(feedback)
        try {
            declareMinions(directive.minionsCount, directive, scenarioRegistry[directive.scenarioId]!!)
            factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
        } catch (e: Exception) {
            log.error(e) { e.message }
            factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.FAILED, error = e.message))
        }
    }

    private suspend fun declareMinions(
        minionsCount: Int,
        directive: MinionsDeclarationDirective,
        scenario: Scenario
    ) {
        log.debug { "Creating $minionsCount minions IDs for the scenario ${directive.scenarioId}" }
        val minionsUnderLoad = mutableListOf<MinionId>()
        for (i in 0 until minionsCount) {
            minionsUnderLoad.add(generateMinionId(scenario.id))
        }
        val dagsUnderLoad = mutableListOf<DirectedAcyclicGraphId>()
        scenario.dags.forEach { dag ->
            val minions = mutableListOf<MinionId>()
            if (dag.isSingleton || !dag.isUnderLoad) {
                // DAGs not under load receive a unique minion.
                val minion = scenario.id + "-lonely-" + idGenerator.short()
                minions.add(minion)
                minionAssignmentKeeper.registerMinionsToAssign(
                    directive.campaignId,
                    directive.scenarioId,
                    listOf(dag.id),
                    listOf(minion),
                    false
                )
            } else {
                dagsUnderLoad += dag.id
                minions += minionsUnderLoad
            }
        }
        // Registers all the non-singleton minions at once.
        minionAssignmentKeeper.registerMinionsToAssign(
            directive.campaignId,
            directive.scenarioId,
            dagsUnderLoad,
            minionsUnderLoad
        )
        minionAssignmentKeeper.completeUnassignedMinionsRegistration(directive.campaignId, directive.scenarioId)

        val minionsAssignmentDirective = MinionsAssignmentDirective(
            directive.campaignId,
            scenario.id
        )
        factoryChannel.publishDirective(minionsAssignmentDirective)
    }

    private fun generateMinionId(scenarioId: ScenarioId): MinionId {
        return scenarioId + "-" + idGenerator.short()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
