package io.qalipsis.core.factory.orchestration.directives

import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.feedbacks.CompleteMinionFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.rampup.RampUpConfiguration
import jakarta.inject.Singleton
import org.slf4j.event.Level

@Singleton
internal class FactoryCampaignManagerImpl(
    private val minionsKeeper: MinionsKeeper,
    private val meterRegistry: MeterRegistry,
    private val scenarioRegistry: ScenarioRegistry,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val idGenerator: IdGenerator,
    private val factoryConfiguration: FactoryConfiguration
) : FactoryCampaignManager {

    @KTestable
    private var runningCampaign: CampaignId = ""

    @KTestable
    private val runningScenarios = mutableSetOf<ScenarioId>()

    override val feedbackNodeId: String
        get() = factoryConfiguration.nodeId

    @LogInput(Level.DEBUG)
    override suspend fun initCampaign(campaignId: CampaignId, scenariosIds: Collection<ScenarioId>) {
        runningScenarios.clear()
        val eligibleScenarios = scenariosIds.filter { it in scenarioRegistry }
        if (eligibleScenarios.isNotEmpty()) {
            runningCampaign = campaignId
            runningScenarios += eligibleScenarios
        }
    }

    @LogInput
    override fun isLocallyExecuted(campaignId: CampaignId): Boolean {
        return runningCampaign == campaignId
    }

    @LogInput
    override fun isLocallyExecuted(campaignId: CampaignId, scenarioId: ScenarioId): Boolean {
        return runningCampaign == campaignId && scenarioId in runningScenarios
    }

    @LogInput(Level.DEBUG)
    override suspend fun warmUpCampaignScenario(campaignId: CampaignId, scenarioId: ScenarioId) {
        if (isLocallyExecuted(campaignId, scenarioId)) {
            log.info { "Starting campaign $campaignId on scenario $scenarioId" }
            scenarioRegistry[scenarioId]!!.start(campaignId)
            minionsKeeper.startSingletons(scenarioId)
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun prepareMinionsRampUp(
        campaignId: CampaignId,
        scenario: Scenario,
        rampUpConfiguration: RampUpConfiguration
    ): List<MinionStartDefinition> {
        val minionsStartDefinitions = mutableListOf<MinionStartDefinition>()
        val minionsUnderLoad = minionAssignmentKeeper.getIdsOfMinionsUnderLoad(campaignId, scenario.id).toMutableList()
        val rampUpStrategyIterator =
            scenario.rampUpStrategy.iterator(minionsUnderLoad.size, rampUpConfiguration.speedFactor)
        var start = System.currentTimeMillis() + rampUpConfiguration.startOffsetMs

        log.debug { "Creating the ramp-up for ${minionsUnderLoad.size} minions on campaign $campaignId of scenario ${scenario.id}" }
        while (minionsUnderLoad.isNotEmpty()) {
            val nextStartingLine = rampUpStrategyIterator.next()
            require(nextStartingLine.count >= 0) { "The number of minions to start at next starting line cannot be negative, but was ${nextStartingLine.count}" }
            require(nextStartingLine.offsetMs > 0) { "The time offset of the next starting line should be strictly positive, but was ${nextStartingLine.offsetMs} ms" }
            start += nextStartingLine.offsetMs

            for (i in 0 until nextStartingLine.count.coerceAtMost(minionsUnderLoad.size)) {
                minionsStartDefinitions.add(MinionStartDefinition(minionsUnderLoad.removeFirst(), start))
            }
        }

        log.debug { "Ramp-up creation is complete on campaign $campaignId of scenario ${scenario.id}" }
        return minionsStartDefinitions
    }

    @LogInput
    override suspend fun notifyCompleteMinion(
        minionId: MinionId,
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        dagId: DirectedAcyclicGraphId
    ) {
        val completionState =
            minionAssignmentKeeper.executionComplete(campaignId, scenarioId, minionId, listOf(dagId))
        if (completionState.minionComplete) {
            feedbackFactoryChannel.publish(
                CompleteMinionFeedback(
                    key = idGenerator.short(),
                    campaignId = campaignId,
                    scenarioId = scenarioId,
                    minionId = minionId,
                    nodeId = factoryConfiguration.nodeId,
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        if (completionState.scenarioComplete) {
            feedbackFactoryChannel.publish(
                EndOfCampaignScenarioFeedback(
                    key = idGenerator.short(),
                    campaignId = campaignId,
                    scenarioId = scenarioId,
                    nodeId = factoryConfiguration.nodeId,
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        if (completionState.campaignComplete) {
            feedbackFactoryChannel.publish(
                EndOfCampaignFeedback(
                    key = idGenerator.short(),
                    campaignId = campaignId,
                    nodeId = factoryConfiguration.nodeId,
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun shutdownScenario(campaignId: CampaignId, scenarioId: ScenarioId) {
        scenarioRegistry[scenarioId]!!.stop(campaignId)
    }

    @LogInput(Level.DEBUG)
    override suspend fun shutdownCampaign(campaignId: CampaignId) {
        if (runningCampaign == campaignId) {
            minionsKeeper.shutdownAll()
            runningScenarios.forEach { scenarioRegistry[it]!!.stop(campaignId) }

            runningScenarios.clear()
            runningCampaign = ""
            // Removes all the meters.
            meterRegistry.clear()
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}