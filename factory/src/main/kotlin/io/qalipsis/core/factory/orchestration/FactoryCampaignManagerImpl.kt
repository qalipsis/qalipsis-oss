package io.qalipsis.core.factory.orchestration

import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.steps.ContextConsumer
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
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
import java.util.Optional

@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class FactoryCampaignManagerImpl(
    private val minionsKeeper: MinionsKeeper,
    private val meterRegistry: MeterRegistry,
    private val scenarioRegistry: ScenarioRegistry,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryChannel: FactoryChannel,
    private val sharedStateRegistry: SharedStateRegistry,
    private val contextConsumer: Optional<ContextConsumer>,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundScope: CoroutineScope,
    @Property(name = "factory.graceful-shutdown.minion", defaultValue = "1s")
    private val minionGracefulShutdown: Duration = Duration.ofSeconds(1),
    @Property(name = "factory.graceful-shutdown.scenario", defaultValue = "10s")
    private val scenarioGracefulShutdown: Duration = Duration.ofSeconds(10),
    @Property(name = "factory.graceful-shutdown.campaign", defaultValue = "60s")
    private val campaignGracefulShutdown: Duration = Duration.ofSeconds(60)
) : FactoryCampaignManager {

    @KTestable
    override var runningCampaign: Campaign = EMPTY_CAMPAIGN

    @KTestable
    private val runningScenarios = concurrentSet<ScenarioName>()

    @LogInput
    override fun isLocallyExecuted(campaignName: CampaignName): Boolean {
        return runningCampaign.campaignName == campaignName
    }

    @LogInput
    override fun isLocallyExecuted(campaignName: CampaignName, scenarioName: ScenarioName): Boolean {
        return runningCampaign.campaignName == campaignName && scenarioName in runningScenarios
    }

    @LogInput(Level.DEBUG)
    override suspend fun init(campaign: Campaign) {
        runningScenarios.clear()
        val eligibleScenarios =
            campaign.assignments.asSequence().map { it.scenarioName }.filter { it in scenarioRegistry }.toList()
        if (eligibleScenarios.isNotEmpty()) {
            runningCampaign = campaign
            runningScenarios += eligibleScenarios
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun warmUpCampaignScenario(campaignName: CampaignName, scenarioName: ScenarioName) {
        if (isLocallyExecuted(campaignName, scenarioName)) {
            log.info { "Starting campaign $campaignName on scenario $scenarioName" }
            scenarioRegistry[scenarioName]!!.start(campaignName)
            minionsKeeper.startSingletons(scenarioName)
            if (contextConsumer.isPresent) {
                tryAndLogOrNull(log) {
                    contextConsumer.get().start()
                }
            }
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun prepareMinionsRampUp(
        campaignName: CampaignName,
        scenario: Scenario,
        rampUpConfiguration: RampUpConfiguration
    ): List<MinionStartDefinition> {
        val minionsStartDefinitions = mutableListOf<MinionStartDefinition>()
        val minionsUnderLoad =
            minionAssignmentKeeper.getIdsOfMinionsUnderLoad(campaignName, scenario.name).toMutableList()
        val rampUpStrategyIterator =
            scenario.rampUpStrategy.iterator(minionsUnderLoad.size, rampUpConfiguration.speedFactor)
        var start = System.currentTimeMillis() + rampUpConfiguration.startOffsetMs

        log.debug { "Creating the ramp-up for ${minionsUnderLoad.size} minions on campaign $campaignName of scenario ${scenario.name}" }
        while (minionsUnderLoad.isNotEmpty()) {
            val nextStartingLine = rampUpStrategyIterator.next()
            require(nextStartingLine.count >= 0) { "The number of minions to start at next starting line cannot be negative, but was ${nextStartingLine.count}" }
            require(nextStartingLine.offsetMs > 0) { "The time offset of the next starting line should be strictly positive, but was ${nextStartingLine.offsetMs} ms" }
            start += nextStartingLine.offsetMs

            for (i in 0 until nextStartingLine.count.coerceAtMost(minionsUnderLoad.size)) {
                minionsStartDefinitions.add(MinionStartDefinition(minionsUnderLoad.removeFirst(), start))
            }
        }

        log.debug { "Ramp-up creation is complete on campaign $campaignName of scenario ${scenario.name}" }
        return minionsStartDefinitions
    }

    @LogInput
    override suspend fun notifyCompleteMinion(
        minionId: MinionId,
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        dagId: DirectedAcyclicGraphName
    ) {
        val completionState =
            minionAssignmentKeeper.executionComplete(campaignName, scenarioName, minionId, listOf(dagId))
        log.trace { "Completing minion $minionId of scenario $scenarioName in campaign $campaignName returns $completionState" }
        // FIXME Generates CompleteMinionFeedbacks for several minions (based upon elapsed time and/or count), otherwise it makes the system overloaded.
        /*if (completionState.minionComplete) {
            factoryChannel.publishFeedback(
                CompleteMinionFeedback(
                    key = idGenerator.short(),
                    campaignName = campaignName,
                    scenarioName = scenarioName,
                    minionId = minionId,
                    nodeId = factoryConfiguration.nodeId,
                    status = FeedbackStatus.COMPLETED
                )
            )
        }*/
        if (completionState.scenarioComplete) {
            factoryChannel.publishFeedback(
                EndOfCampaignScenarioFeedback(
                    campaignName = campaignName,
                    scenarioName = scenarioName,
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
    }

    override suspend fun shutdownMinions(campaignName: CampaignName, minionIds: Collection<MinionId>) {
        sharedStateRegistry.clear(minionIds)
        minionIds.map { minionId ->
            backgroundScope.async {
                kotlin.runCatching {
                    withCancellableTimeout(minionGracefulShutdown) {
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
    override suspend fun shutdownScenario(campaignName: CampaignName, scenarioName: ScenarioName) {
        withCancellableTimeout(scenarioGracefulShutdown) {
            try {
                if (contextConsumer.isPresent) {
                    tryAndLogOrNull(log) {
                        contextConsumer.get().stop()
                    }
                }
                scenarioRegistry[scenarioName]!!.stop(campaignName)
            } catch (e: Exception) {
                log.trace(e) { "The scenario $scenarioName was not successfully shut down: ${e.message}" }
                throw e
            }
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun close(campaign: Campaign) {
        if (runningCampaign.campaignName == campaign.campaignName) {
            withCancellableTimeout(campaignGracefulShutdown) {
                minionsKeeper.shutdownAll()
                runningScenarios.forEach { scenarioRegistry[it]!!.stop(campaign.campaignName) }
                runningScenarios.clear()
                runningCampaign = EMPTY_CAMPAIGN
                // Removes all the meters.
                meterRegistry.clear()
                sharedStateRegistry.clear()
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

    private companion object {

        val EMPTY_CAMPAIGN = Campaign("", "", "", emptyList())

        val log = logger()
    }
}