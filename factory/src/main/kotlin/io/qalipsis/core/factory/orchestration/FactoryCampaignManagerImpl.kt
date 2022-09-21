/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.orchestration

import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.executionprofile.AcceleratingExecutionProfile
import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.executionprofile.ProgressiveVolumeExecutionProfile
import io.qalipsis.api.executionprofile.RegularExecutionProfile
import io.qalipsis.api.executionprofile.Stage
import io.qalipsis.api.executionprofile.StageExecutionProfile
import io.qalipsis.api.executionprofile.TimeFrameExecutionProfile
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ProgressiveVolumeExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.TimeFrameExecutionProfileConfiguration
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.steps.ContextConsumer
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.time.Duration
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import org.slf4j.event.Level

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
    override fun isLocallyExecuted(campaignKey: CampaignKey): Boolean {
        return runningCampaign.campaignKey == campaignKey
    }

    @LogInput
    override fun isLocallyExecuted(campaignKey: CampaignKey, scenarioName: ScenarioName): Boolean {
        return runningCampaign.campaignKey == campaignKey && scenarioName in runningScenarios
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
    override suspend fun warmUpCampaignScenario(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        if (isLocallyExecuted(campaignKey, scenarioName)) {
            log.info { "Starting campaign $campaignKey on scenario $scenarioName" }
            scenarioRegistry[scenarioName]!!.start(campaignKey)
            minionsKeeper.startSingletons(scenarioName)
            if (contextConsumer.isPresent) {
                tryAndLogOrNull(log) {
                    contextConsumer.get().start()
                }
            }
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun prepareMinionsExecutionProfile(
        campaignKey: CampaignKey,
        scenario: Scenario,
        executionProfileConfiguration: ExecutionProfileConfiguration
    ): List<MinionStartDefinition> {
        val minionsStartDefinitions = mutableListOf<MinionStartDefinition>()
        val minionsUnderLoad =
            minionAssignmentKeeper.getIdsOfMinionsUnderLoad(campaignKey, scenario.name).toMutableList()
        val executionProfileIterator =
            convertExecutionProfile(executionProfileConfiguration, scenario.executionProfile).iterator(
                minionsUnderLoad.size,
                executionProfileConfiguration.speedFactor
            )
        var start = System.currentTimeMillis() + executionProfileConfiguration.startOffsetMs

        log.debug { "Creating the execution profile for ${minionsUnderLoad.size} minions on campaign $campaignKey of scenario ${scenario.name}" }
        while (minionsUnderLoad.isNotEmpty() && executionProfileIterator.hasNext()) {
            val nextStartingLine = executionProfileIterator.next()
            require(nextStartingLine.count >= 0) { "The number of minions to start at next starting line cannot be negative, but was ${nextStartingLine.count}" }
            require(nextStartingLine.offsetMs > 0) { "The time offset of the next starting line should be strictly positive, but was ${nextStartingLine.offsetMs} ms" }
            start += nextStartingLine.offsetMs

            for (i in 0 until nextStartingLine.count.coerceAtMost(minionsUnderLoad.size)) {
                minionsStartDefinitions.add(MinionStartDefinition(minionsUnderLoad.removeFirst(), start))
            }
        }

        log.debug { "Execution profile creation is complete on campaign $campaignKey of scenario ${scenario.name}" }
        return minionsStartDefinitions
    }

    @KTestable
    private fun convertExecutionProfile(configuration: ExecutionProfileConfiguration, defaultExecutionProfile: ExecutionProfile): ExecutionProfile {
        return when (configuration) {
            is AcceleratingExecutionProfileConfiguration -> AcceleratingExecutionProfile(
                configuration.startPeriodMs,
                configuration.accelerator,
                configuration.minPeriodMs,
                configuration.minionsCountProLaunch
            )
            is RegularExecutionProfileConfiguration -> RegularExecutionProfile(
                configuration.periodInMs,
                configuration.minionsCountProLaunch
            )
            is ProgressiveVolumeExecutionProfileConfiguration -> ProgressiveVolumeExecutionProfile(
                configuration.periodMs,
                configuration.minionsCountProLaunchAtStart,
                configuration.multiplier,
                configuration.maxMinionsCountProLaunch
            )
            is StageExecutionProfileConfiguration -> StageExecutionProfile(
                configuration.stages.map { Stage(
                    it.minionsCount,
                    it.rampUpDurationMs,
                    it.totalDurationMs,
                    it.resolutionMs
                ) },
                configuration.completion
            )
            is TimeFrameExecutionProfileConfiguration -> TimeFrameExecutionProfile(
                configuration.periodInMs,
                configuration.timeFrameInMs
            )
            else -> defaultExecutionProfile
        }
    }

    @LogInput
    override suspend fun notifyCompleteMinion(
        minionId: MinionId,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagId: DirectedAcyclicGraphName
    ) {
        val completionState =
            minionAssignmentKeeper.executionComplete(campaignKey, scenarioName, minionId, listOf(dagId))
        log.trace { "Completing minion $minionId of scenario $scenarioName in campaign $campaignKey returns $completionState" }
        // FIXME Generates CompleteMinionFeedbacks for several minions (based upon elapsed time and/or count), otherwise it makes the system overloaded.
        /*if (completionState.minionComplete) {
            factoryChannel.publishFeedback(
                CompleteMinionFeedback(
                    key = idGenerator.short(),
                    campaignKey = campaignKey,
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
                    campaignKey = campaignKey,
                    scenarioName = scenarioName,
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
    }

    override suspend fun shutdownMinions(campaignKey: CampaignKey, minionIds: Collection<MinionId>) {
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
    override suspend fun shutdownScenario(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        withCancellableTimeout(scenarioGracefulShutdown) {
            try {
                if (contextConsumer.isPresent) {
                    tryAndLogOrNull(log) {
                        contextConsumer.get().stop()
                    }
                }
                scenarioRegistry[scenarioName]!!.stop(campaignKey)
            } catch (e: Exception) {
                log.trace(e) { "The scenario $scenarioName was not successfully shut down: ${e.message}" }
                throw e
            }
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun close(campaign: Campaign) {
        if (runningCampaign.campaignKey == campaign.campaignKey) {
            withCancellableTimeout(campaignGracefulShutdown) {
                minionsKeeper.shutdownAll()
                runningScenarios.forEach { scenarioRegistry[it]!!.stop(campaign.campaignKey) }
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