package io.qalipsis.core.factory.orchestration

import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.qalipsis.api.Executors
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
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.rampup.RampUpConfiguration
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import org.slf4j.event.Level
import java.time.Duration

@Singleton
internal class FactoryCampaignManagerImpl(
    private val minionsKeeper: MinionsKeeper,
    private val meterRegistry: MeterRegistry,
    private val scenarioRegistry: ScenarioRegistry,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val idGenerator: IdGenerator,
    private val factoryConfiguration: FactoryConfiguration,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundScope: CoroutineScope,
    @Property(
        name = "minion-shutdown-timeout",
        defaultValue = "1s"
    ) private val minionShutdownTimeout: Duration = Duration.ofSeconds(1),
    @Property(
        name = "scenario-shutdown-timeout",
        defaultValue = "10s"
    ) private val scenarioShutdownTimeout: Duration = Duration.ofSeconds(10),
    @Property(
        name = "campaign-shutdown-timeout",
        defaultValue = "60s"
    ) private val campaignShutdownTimeout: Duration = Duration.ofSeconds(60)
) : FactoryCampaignManager {

    @KTestable
    override var runningCampaign: CampaignId = ""

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
        log.trace { "Completing minion $minionId of scenario $scenarioId in campaign $campaignId returns $completionState" }
        // FIXME Generates CompleteMinionFeedbacks for several minions (based upon elapsed time and/or count), otherwise it makes the system overloaded.
        /*if (completionState.minionComplete) {
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
        }*/
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
    }

    override suspend fun shutdownMinions(campaignId: CampaignId, minionIds: Collection<MinionId>) {
        minionIds.map { minionId ->
            backgroundScope.async {
                kotlin.runCatching {
                    withCancellableTimeout(minionShutdownTimeout) {
                        try {
                            minionsKeeper.shutdownMinion(minionId)
                        } catch (e: Exception) {
                            log.trace(e) { "The minion $minionId was not successfully shut down: ${e.message}" }
                        }
                    }
                }
            }
        }.awaitAll()
    }

    @LogInput(Level.DEBUG)
    override suspend fun shutdownScenario(campaignId: CampaignId, scenarioId: ScenarioId) {
        withCancellableTimeout(scenarioShutdownTimeout) {
            try {
                scenarioRegistry[scenarioId]!!.stop(campaignId)
            } catch (e: Exception) {
                log.trace(e) { "The scenario $scenarioId was not successfully shut down: ${e.message}" }
                throw e
            }
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun shutdownCampaign(campaignId: CampaignId) {
        if (runningCampaign == campaignId) {
            withCancellableTimeout(campaignShutdownTimeout) {
                minionsKeeper.shutdownAll()
                runningScenarios.forEach { scenarioRegistry[it]!!.stop(campaignId) }

                runningScenarios.clear()
                runningCampaign = ""
                // Removes all the meters.
                meterRegistry.clear()
            }
        }
    }

    /**
     * Executes [block], then cancels it if it did not complete before the timeout.
     */
    private suspend fun withCancellableTimeout(timeout: Duration, block: suspend () -> Unit) {
        val deferred = backgroundScope.async { block() }
        try {
            withTimeout(timeout.toMillis()) { deferred.await() }
            deferred.getCompletionExceptionOrNull()?.let { throw it }
        } catch (e: TimeoutCancellationException) {
            deferred.cancel(e)
            throw e
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}