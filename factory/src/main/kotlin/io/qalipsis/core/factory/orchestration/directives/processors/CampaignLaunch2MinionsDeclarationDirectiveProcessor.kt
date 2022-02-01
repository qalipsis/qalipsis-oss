package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.directives.MinionsDeclarationDirectiveReference
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * The [CampaignLaunch2MinionsDeclarationDirectiveProcessor] is responsible for generating the identifiers for the minions to
 * create for each scenario and DAG.
 *
 * @author Eric Jessé
 */
@Singleton
internal class CampaignLaunch2MinionsDeclarationDirectiveProcessor(
    private val scenarioRegistry: ScenarioRegistry,
    private val directiveRegistry: DirectiveRegistry,
    private val directiveProducer: DirectiveProducer,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val idGenerator: IdGenerator,
    private val factoryConfiguration: FactoryConfiguration,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryCampaignManager: FactoryCampaignManager
) : DirectiveProcessor<MinionsDeclarationDirectiveReference> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsDeclarationDirectiveReference
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId, directive.scenarioId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: MinionsDeclarationDirectiveReference) {
        scenarioRegistry[directive.scenarioId]?.let { scenario ->
            directiveRegistry.read(directive)?.let { minionsCount ->
                val feedback = MinionsDeclarationFeedback(
                    key = idGenerator.short(),
                    campaignId = directive.campaignId,
                    scenarioId = directive.scenarioId,
                    nodeId = factoryCampaignManager.feedbackNodeId,
                    status = FeedbackStatus.IN_PROGRESS
                )
                feedbackFactoryChannel.publish(feedback)
                try {
                    declareMinions(minionsCount, directive, scenario)
                    feedbackFactoryChannel.publish(
                        feedback.copy(
                            key = idGenerator.short(),
                            status = FeedbackStatus.COMPLETED
                        )
                    )
                } catch (e: Exception) {
                    log.error(e) { e.message }
                    feedbackFactoryChannel.publish(
                        feedback.copy(
                            key = idGenerator.short(),
                            status = FeedbackStatus.FAILED,
                            error = e.message
                        )
                    )
                }
            }
        }
    }

    private suspend fun declareMinions(
        minionsCount: Int,
        directive: MinionsDeclarationDirectiveReference,
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
            scenario.id,
            key = idGenerator.short(),
            channel = factoryConfiguration.directiveRegistry.broadcastDirectivesChannel
        )
        directiveProducer.publish(minionsAssignmentDirective)
    }

    private fun generateMinionId(scenarioId: ScenarioId): MinionId {
        return scenarioId + "-" + idGenerator.short()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
