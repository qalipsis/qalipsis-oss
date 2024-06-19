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
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.executionprofile.AcceleratingExecutionProfile
import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.executionprofile.ImmediateExecutionProfile
import io.qalipsis.api.executionprofile.MinionsStartingLine
import io.qalipsis.api.executionprofile.PercentageStage
import io.qalipsis.api.executionprofile.PercentageStageExecutionProfile
import io.qalipsis.api.executionprofile.ProgressiveVolumeExecutionProfile
import io.qalipsis.api.executionprofile.RegularExecutionProfile
import io.qalipsis.api.executionprofile.Stage
import io.qalipsis.api.executionprofile.StageExecutionProfile
import io.qalipsis.api.executionprofile.TimeFrameExecutionProfile
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.ScenarioStartStopConfiguration
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.annotations.LogOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ImmediateExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.PercentageStageExecutionProfileConfiguration
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import org.slf4j.event.Level
import java.time.Duration
import java.time.Instant
import java.util.Optional

@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class FactoryCampaignManagerImpl(
    private val minionsKeeper: MinionsKeeper,
    private val scenarioRegistry: ScenarioRegistry,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryChannel: FactoryChannel,
    private val sharedStateRegistry: SharedStateRegistry,
    private val contextConsumer: Optional<ContextConsumer>,
    private val campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundScope: CoroutineScope,
    @Property(name = "factory.graceful-shutdown.minion", defaultValue = "1s")
    private val minionGracefulShutdown: Duration = Duration.ofSeconds(1),
    @Property(name = "factory.graceful-shutdown.scenario", defaultValue = "10s")
    private val scenarioGracefulShutdown: Duration = Duration.ofSeconds(10),
    @Property(name = "factory.graceful-shutdown.campaign", defaultValue = "60s")
    private val campaignGracefulShutdown: Duration = Duration.ofSeconds(60)
) : FactoryCampaignManager {

    /**
     * Currently running campaign in the factory.
     */
    @KTestable
    override var runningCampaign: Campaign = EMPTY_CAMPAIGN

    /**
     * Scenarios to be globally executed in the campaign, and known in the current factory.
     */
    @KTestable
    private val assignableScenariosExecutionProfiles = mutableMapOf<ScenarioName, ExecutionProfile>()

    @LogInputAndOutput
    override fun isLocallyExecuted(campaignKey: CampaignKey): Boolean {
        return runningCampaign.campaignKey == campaignKey
    }

    @LogInputAndOutput
    override fun isLocallyExecuted(campaignKey: CampaignKey, scenarioName: ScenarioName): Boolean {
        return runningCampaign.campaignKey == campaignKey && scenarioName in assignableScenariosExecutionProfiles.keys
    }

    @LogInput(Level.DEBUG)
    override suspend fun init(campaign: Campaign) {
        assignableScenariosExecutionProfiles.clear()
        val eligibleScenarios =
            campaign.assignments.asSequence().map { it.scenarioName }.filter { it in scenarioRegistry }.toList()
        if (eligibleScenarios.isNotEmpty()) {
            runningCampaign = campaign

            // Calculate the execution profiles for all the supported scenarios.
            val executionProfiles = eligibleScenarios.associateWith { scenarioName ->
                convertExecutionProfile(
                    campaign.scenarios[scenarioName]!!.executionProfileConfiguration,
                    scenarioRegistry[scenarioName]!!.executionProfile
                )
            }
            assignableScenariosExecutionProfiles.putAll(executionProfiles)
        }
    }

    @KTestable
    private fun convertExecutionProfile(
        configuration: ExecutionProfileConfiguration,
        defaultExecutionProfile: ExecutionProfile
    ): ExecutionProfile {
        return when (configuration) {
            is AcceleratingExecutionProfileConfiguration -> AcceleratingExecutionProfile(
                configuration.startPeriodMs,
                configuration.accelerator,
                configuration.minPeriodMs,
                configuration.minionsCountProLaunch
            )

            is ImmediateExecutionProfileConfiguration -> ImmediateExecutionProfile()

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

            is PercentageStageExecutionProfileConfiguration -> PercentageStageExecutionProfile(
                configuration.completion,
                configuration.stages.map {
                    PercentageStage(
                        it.minionsPercentage,
                        it.rampUpDurationMs,
                        it.totalDurationMs,
                        it.resolutionMs
                    )
                }
            )

            is StageExecutionProfileConfiguration -> StageExecutionProfile(
                configuration.completion,
                configuration.stages.map {
                    Stage(
                        it.minionsCount,
                        it.rampUpDurationMs,
                        it.totalDurationMs,
                        it.resolutionMs
                    )
                }
            )

            is TimeFrameExecutionProfileConfiguration -> TimeFrameExecutionProfile(
                configuration.periodInMs,
                configuration.timeFrameInMs
            )

            else -> defaultExecutionProfile
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun warmUpCampaignScenario(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        if (isLocallyExecuted(campaignKey, scenarioName)) {
            log.debug { "Loading the starting schedule plan for the campaign $campaignKey on scenario $scenarioName into memory" }
            minionAssignmentKeeper.readSchedulePlan(campaignKey, scenarioName)

            log.info { "Starting campaign $campaignKey on scenario $scenarioName" }
            scenarioRegistry[scenarioName]!!.start(object : ScenarioStartStopConfiguration {
                override val campaignKey = campaignKey
                override val campaignReportLiveStateRegistry =
                    this@FactoryCampaignManagerImpl.campaignReportLiveStateRegistry
            })

            if (contextConsumer.isPresent) {
                tryAndLogOrNull(log) {
                    contextConsumer.get().start()
                }
            }
        }
    }

    @LogInput(Level.DEBUG)
    @LogOutput(Level.TRACE)
    override suspend fun prepareMinionsExecutionProfile(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        executionProfileConfiguration: ExecutionProfileConfiguration
    ): List<MinionsStartingLine> {

        var remainingMinionsUnderLoadCount = minionAssignmentKeeper.countMinionsUnderLoad(campaignKey, scenarioName)
        val executionProfile = assignableScenariosExecutionProfiles[scenarioName]!!
        log.debug { "Using the execution profile $executionProfile" }
        val executionProfileIterator = executionProfile.iterator(
            totalMinionsCount = remainingMinionsUnderLoadCount,
            speedFactor = runningCampaign.speedFactor
        )
        var start = 0L

        // Notifies all the execution profiles that the campaign is now effectively starting.
        assignableScenariosExecutionProfiles.values.forEach { it.notifyStart(runningCampaign.speedFactor) }

        val result = mutableListOf<MinionsStartingLine>()
        log.debug { "Creating the execution profile for $remainingMinionsUnderLoadCount minions on campaign $campaignKey of scenario $scenarioName" }
        while (remainingMinionsUnderLoadCount > 0 && executionProfileIterator.hasNext()) {
            val nextStartingLine = executionProfileIterator.next()
            require(nextStartingLine.count >= 0) { "The number of minions to start at next starting line cannot be negative, but was ${nextStartingLine.count}" }
            val nextStart = start + nextStartingLine.offsetMs
            require(nextStart >= start) { "The next starting line should not be in the past, but was planned ${System.currentTimeMillis() - start} ms ago" }

            val minionsCountToStart = nextStartingLine.count.coerceAtMost(remainingMinionsUnderLoadCount)
            result += nextStartingLine.copy(
                offsetMs = nextStart,
                count = minionsCountToStart
            )
            remainingMinionsUnderLoadCount -= minionsCountToStart

            start = nextStart
        }

        assert(remainingMinionsUnderLoadCount == 0) { "$remainingMinionsUnderLoadCount minions could not be scheduled" }
        log.debug { "Ramp-up creation is complete on campaign $campaignKey for scenario $scenarioName" }
        return result
    }

    @LogInput
    override suspend fun notifyCompleteMinion(
        minionId: MinionId,
        minionStart: Instant,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagId: DirectedAcyclicGraphName
    ) {
        // Checks whether the minion is eligible to be restarted if it is complete.
        val minionsExecutionElapsedTime = Duration.between(minionStart, Instant.now())
        val minion = minionsKeeper[minionId]
        val mightRestartMinion = !minion.isSingleton && canReplay(minionsExecutionElapsedTime)
                && assignableScenariosExecutionProfiles[scenarioName]!!.canReplay(minionsExecutionElapsedTime)

        // Verifies the actual completion state of the minion, scenario and campaign.
        val completionState =
            minionAssignmentKeeper.executionComplete(
                campaignKey,
                scenarioName,
                minionId,
                listOf(dagId),
                mightRestartMinion
            )
        log.trace { "Completing minion $minionId of scenario $scenarioName in campaign $campaignKey returns $completionState" }
        if (completionState.minionComplete) {
            if (mightRestartMinion) {
                log.trace { "Restarting the minion $minionId on the scenario $scenarioName" }
                minionsKeeper.restartMinion(minionId)
            } else {
                minionsKeeper.shutdownMinion(minionId)
                // FIXME Generates CompleteMinionFeedbacks for several minions (based upon elapsed time and/or count), otherwise it makes the system overloaded.
                /* factoryChannel.publishFeedback(
                     CompleteMinionFeedback(
                         key = idGenerator.short(),
                         campaignKey = campaignKey,
                         scenarioName = scenarioName,
                         minionId = minionId,
                         nodeId = factoryConfiguration.nodeId,
                         status = FeedbackStatus.COMPLETED
                     )
                 )*/
            }
        }
        if (completionState.scenarioComplete) {
            log.debug { "The scenario $scenarioName is now complete" }
            factoryChannel.publishFeedback(
                EndOfCampaignScenarioFeedback(
                    campaignKey = campaignKey,
                    scenarioName = scenarioName,
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
    }

    /**
     * Verifies whether the minion has enough time to be replayed until the campaign timeout.
     */
    private fun canReplay(minionExecutionDuration: Duration): Boolean {
        return runningCampaign.softTimeout?.let {
            Duration.between(Instant.now(), it) > minionExecutionDuration
        } ?: true
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
                scenarioRegistry[scenarioName]!!.stop(object : ScenarioStartStopConfiguration {
                    override val campaignKey = campaignKey
                    override val campaignReportLiveStateRegistry =
                        this@FactoryCampaignManagerImpl.campaignReportLiveStateRegistry
                })
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
                val shutdownConfiguration = object : ScenarioStartStopConfiguration {
                    override val campaignKey = campaign.campaignKey
                    override val campaignReportLiveStateRegistry =
                        this@FactoryCampaignManagerImpl.campaignReportLiveStateRegistry
                }
                assignableScenariosExecutionProfiles.keys.forEach {
                    scenarioRegistry[it]!!.stop(shutdownConfiguration)
                }
                runningCampaign = EMPTY_CAMPAIGN
                sharedStateRegistry.clear()
                assignableScenariosExecutionProfiles.clear()
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

        val EMPTY_CAMPAIGN = Campaign("", 1.0, 0, Instant.MIN, Instant.MIN, "", "", emptyMap(), emptyList())

        val log = logger()
    }
}