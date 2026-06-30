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

package io.qalipsis.core.head.inmemory

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.inmemory.consolereporter.ConsoleCampaignProgressionReporter
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.core.head.model.StepExecutionDetails
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.report.CampaignMeterEnricher
import io.qalipsis.core.head.report.CampaignReportProvider
import io.qalipsis.core.head.report.MeterDistribution
import io.qalipsis.core.head.report.toCampaignExecutionDetails
import io.qalipsis.core.head.report.toCampaignReport
import io.qalipsis.core.head.zone.ZoneService
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.annotation.Nullable
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.event.Level
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Singleton
@Requires(env = [STANDALONE])
class StandaloneCampaignReportStateKeeperImpl(
    private val idGenerator: IdGenerator,
    @Nullable private val consoleCampaignProgressionReporter: ConsoleCampaignProgressionReporter?,
    @Value("\${campaign.report.cache.time-to-live:PT10M}") private val cacheExpire: Duration,
    @Named(TaskExecutors.SCHEDULED) private val taskScheduler: TaskScheduler,
    private val campaignService: BeanProvider<CampaignService>,
    private val zoneService: ZoneService,
    private val campaignMeterEnricher: CampaignMeterEnricher
) : CampaignReportStateKeeper, CampaignReportLiveStateRegistry, ProcessBlocker, CampaignReportProvider {

    @KTestable
    private val campaignStates =
        ConcurrentHashMap<CampaignKey, MutableMap<ScenarioName, InMemoryScenarioReportingExecutionState>>()

    @KTestable
    private val forcedStatus = ConcurrentHashMap<CampaignKey, ForcedStatus>()

    /**
     * Counter for the running scenarios to block the process until the campaign is completed.
     */
    private val runningCampaignLatch = Latch(false, name = "campaign-report-state-keeper-running-campaign")

    override fun getOrder() = Int.MAX_VALUE

    override suspend fun join() {
        runningCampaignLatch.await()
    }

    @PreDestroy
    override fun cancel() {
        runningCampaignLatch.cancel()
    }

    @LogInput(Level.DEBUG)
    override suspend fun start(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        consoleCampaignProgressionReporter?.start(scenarioName)
        runningCampaignLatch.lock()
        campaignStates.computeIfAbsent(campaignKey) { ConcurrentHashMap() }[scenarioName] =
            InMemoryScenarioReportingExecutionState(scenarioName)
    }

    @LogInput(Level.DEBUG)
    override suspend fun complete(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        consoleCampaignProgressionReporter?.complete(scenarioName)
        campaignStates[campaignKey]!![scenarioName]!!.end = Instant.now()
    }

    @LogInput(Level.DEBUG)
    override suspend fun complete(campaignKey: CampaignKey, result: ExecutionStatus, failureReason: String?) {
        forcedStatus[campaignKey] = ForcedStatus(result, failureReason)
        failureReason?.let {
            campaignStates[campaignKey]?.forEach { (scenarioName, state) ->
                // Adds the failure message on all the non-ended scenarios.
                if (state.status == null) {
                    consoleCampaignProgressionReporter?.attachMessage(
                        scenarioName, "", ReportMessageSeverity.ERROR, idGenerator.short(),
                        failureReason
                    )
                }
            }
        }
        consoleCampaignProgressionReporter?.stop()
        runningCampaignLatch.cancel()
        // Deletes the campaign key after the retention delay.
        taskScheduler.schedule(cacheExpire) { campaignStates.remove(campaignKey) }
    }

    @LogInput(Level.DEBUG)
    override suspend fun abort(campaignKey: CampaignKey) {
        forcedStatus[campaignKey] = ForcedStatus(ExecutionStatus.ABORTED)
        val abortTimestamp = Instant.now()
        campaignStates[campaignKey]
            ?.filterValues { state -> state.end == null && state.abort == null }
            ?.forEach { (_, state) ->
                state.abort = abortTimestamp
                state.status = ExecutionStatus.ABORTED
            }
        runningCampaignLatch.cancel()
        // Deletes the campaign key after the retention delay.
        taskScheduler.schedule(cacheExpire) { campaignStates.remove(campaignKey) }
    }

    @LogInputAndOutput
    override suspend fun put(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        messageId: String?,
        message: String
    ): String {
        return (messageId?.takeIf(String::isNotBlank) ?: idGenerator.short()).also { id ->
            consoleCampaignProgressionReporter?.attachMessage(scenarioName, stepName, severity, id, message)
            campaignStates[campaignKey]!![scenarioName]!!.keyedMessages[id] =
                ReportMessage(stepName, id, severity, message)
        }
    }

    override suspend fun clear(campaignKey: CampaignKey) {
        campaignStates.remove(campaignKey)
        runningCampaignLatch.lock()
    }

    @LogInput
    override suspend fun delete(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        messageId: Any
    ) {
        consoleCampaignProgressionReporter?.detachMessage(scenarioName, stepName, messageId)
        campaignStates[campaignKey]!![scenarioName]!!.keyedMessages.remove(messageId)
    }

    @LogInput
    override suspend fun recordStartedMinion(campaignKey: CampaignKey, scenarioName: ScenarioName, count: Int) {
        consoleCampaignProgressionReporter?.recordStartedMinion(scenarioName, count)
        campaignStates[campaignKey]!![scenarioName]!!.startedMinionsCounter.addAndGet(count)
    }

    @LogInput
    override suspend fun recordCompletedMinion(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        count: Int
    ) {
        consoleCampaignProgressionReporter?.recordCompletedMinion(scenarioName, count)
        campaignStates[campaignKey]!![scenarioName]!!.completedMinionsCounter.addAndGet(count)
    }

    @LogInput
    override suspend fun recordSuccessfulStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ) {
        consoleCampaignProgressionReporter?.recordSuccessfulStepExecution(scenarioName, stepName, count)
        val scenarioState = campaignStates[campaignKey]!![scenarioName]!!
        scenarioState.successfulStepExecutionsCounter.addAndGet(count)
        scenarioState.stepByName(stepName)?.successfulExecutionsCounter?.addAndGet(count.toLong())
    }

    @LogInputAndOutput
    override suspend fun recordSuccessfulStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        dagId: DirectedAcyclicGraphName,
        underLoad: Boolean
    ) {
        consoleCampaignProgressionReporter?.recordSuccessfulStepInitialization(campaignKey, scenarioName, stepName)
        campaignStates[campaignKey]!![scenarioName]!!.registerStep(
            stepName, dagId,
            InMemoryStepExecutionState(name = stepName, dagId = dagId, isUnderLoad = underLoad, initialized = true)
        )
    }

    @LogInputAndOutput
    override suspend fun recordFailedStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        dagId: DirectedAcyclicGraphName,
        underLoad: Boolean,
        cause: Throwable?
    ) {
        consoleCampaignProgressionReporter?.recordFailedStepInitialization(campaignKey, scenarioName, stepName, cause)
        val causeName = cause?.javaClass?.canonicalName?.let { "$it: " } ?: ""
        val causeMessage = cause?.message ?: "<Unknown>"
        campaignStates[campaignKey]!![scenarioName]!!.registerStep(
            stepName, dagId,
            InMemoryStepExecutionState(
                name = stepName,
                dagId = dagId,
                isUnderLoad = underLoad,
                initialized = false,
                initializationError = "$causeName$causeMessage"
            )
        )
    }

    @LogInput
    override suspend fun recordFailedStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int,
        cause: Throwable?
    ) {
        consoleCampaignProgressionReporter?.recordFailedStepExecution(scenarioName, stepName, count, cause)
        val scenarioState = campaignStates[campaignKey]!![scenarioName]!!
        scenarioState.failedStepExecutionsCounter.addAndGet(count)
        scenarioState.stepByName(stepName)?.failedExecutionsCounter?.addAndGet(count.toLong())
    }

    @LogInputAndOutput
    override suspend fun generateReport(campaignKey: CampaignKey): CampaignReport? {
        join()
        val campaignState = campaignStates[campaignKey] ?: throw NoSuchElementException()
        val scenariosReports =
            campaignState.map { (_, runningScenarioCampaign) -> runningScenarioCampaign.toReport(campaignKey) }
        return scenariosReports.toCampaignReport(knownResult = forcedStatus[campaignKey]?.executionStatus)
    }

    @LogInputAndOutput
    override suspend fun retrieveCampaignsReports(
        tenant: String,
        campaignKeys: Collection<CampaignKey>
    ): Collection<CampaignExecutionDetails> {
        val knownKeys = campaignKeys.filter { campaignStates.containsKey(it) }
        if (knownKeys.isEmpty()) return emptyList()

        val configByCampaignKey = coroutineScope {
            knownKeys.map { campaignKey ->
                async {
                    campaignKey to runCatching {
                        campaignService.get().retrieveConfiguration(
                            tenant,
                            campaignKey
                        )
                    }.getOrNull()
                }
            }.awaitAll()
        }.toMap()

        // Resolve all zones in one call using the union of all zone keys from all configs.
        val allZoneKeys = configByCampaignKey.values.filterNotNull()
            .flatMap { cfg -> cfg.scenarios.values.flatMap { it.zones?.keys ?: emptySet() } }
            .toSet()
        val allResolvedZones = zoneService.resolve(tenant, allZoneKeys)
        val resolvedZonesByKey = allResolvedZones.associateBy { it.key }

        // Bulk-fetch all meters for all campaigns.
        val scenariosOfAllCampaigns = knownKeys.flatMap { key ->
            campaignStates[key]?.keys ?: emptyList()
        }.distinct()
        val metersByCampaignKey = campaignMeterEnricher.distribute(tenant, knownKeys, scenariosOfAllCampaigns)

        return knownKeys.mapNotNull { campaignKey ->
            val scenariosReports = campaignStates[campaignKey]
                ?.mapNotNull { (_, state) -> state.toReport(campaignKey) }
            if (scenariosReports.isNullOrEmpty()) return@mapNotNull null

            val config = configByCampaignKey[campaignKey]
            val meterDistribution = metersByCampaignKey[campaignKey]
                ?: MeterDistribution(emptyList(), emptyMap(), emptyMap())

            val campaignZoneKeysFromConfig = config?.scenarios?.values
                ?.flatMap { it.zones?.keys ?: emptySet() }?.toSet() ?: emptySet()
            val campaignResolvedZones = campaignZoneKeysFromConfig.mapNotNull { resolvedZonesByKey[it] }

            val report = scenariosReports.toCampaignExecutionDetails(
                knownResult = forcedStatus[campaignKey]?.executionStatus,
                failureReason = forcedStatus[campaignKey]?.failureReason
            )

            val campaign = runCatching { campaignService.get().retrieve(tenant, campaignKey) }.getOrNull()
            val campaignName = campaign?.name ?: campaignKey

            val scenariosExecutionDetails = scenariosReports.map { scenarioReport ->
                val zoneDistribution = config?.scenarios?.get(scenarioReport.scenarioName)?.zones ?: emptyMap()
                val scheduledMinions =
                    campaign?.configuredScenarios?.find { it.name == scenarioReport.scenarioName }?.minionsCount
                val steps = scenarioReport.steps.map { stepReport ->
                    val stepMessages = scenarioReport.messages.filter { it.stepName == stepReport.name }
                    val stepStatus = when {
                        !stepReport.initialized -> ExecutionStatus.FAILED
                        stepReport.initializationError != null -> ExecutionStatus.FAILED
                        stepMessages.any { it.severity == ReportMessageSeverity.ERROR } -> ExecutionStatus.FAILED
                        stepMessages.any { it.severity == ReportMessageSeverity.WARN } -> ExecutionStatus.WARNING
                        stepReport.failedExecutions > 0 -> ExecutionStatus.WARNING
                        else -> ExecutionStatus.SUCCESSFUL
                    }
                    StepExecutionDetails(
                        name = stepReport.name,
                        successfulExecutions = stepReport.successfulExecutions,
                        failedExecutions = stepReport.failedExecutions,
                        status = stepStatus,
                        messages = stepMessages,
                        meters = meterDistribution.stepMeters(scenarioReport.scenarioName, stepReport.name)
                    )
                }
                ScenarioExecutionDetails(
                    id = scenarioReport.scenarioName,
                    name = scenarioReport.scenarioName,
                    start = scenarioReport.start,
                    end = scenarioReport.end,
                    scheduledMinions = scheduledMinions,
                    startedMinions = scenarioReport.startedMinions,
                    completedMinions = scenarioReport.completedMinions,
                    successfulExecutions = scenarioReport.successfulExecutions?.toLong(),
                    failedExecutions = scenarioReport.failedExecutions?.toLong(),
                    status = scenarioReport.status,
                    messages = scenarioReport.messages.filter { msg -> steps.none { it.name == msg.stepName } },
                    steps = steps,
                    meters = meterDistribution.scenarioMeters(scenarioReport.scenarioName),
                    zoneDistribution = zoneDistribution
                )
            }

            CampaignExecutionDetails(
                version = report.end ?: Instant.now(),
                key = campaignKey,
                creation = report.start ?: Instant.now(),
                name = campaignName,
                speedFactor = 1.0,
                scheduledMinions = campaign?.scheduledMinions,
                start = report.start,
                end = report.end,
                status = report.status,
                zones = campaignZoneKeysFromConfig,
                startedMinions = report.startedMinions,
                completedMinions = report.completedMinions,
                successfulExecutions = report.successfulExecutions,
                failedExecutions = report.failedExecutions,
                scenarios = scenariosExecutionDetails,
                meters = meterDistribution.campaignMeters
            ).also { it.resolvedZones = campaignResolvedZones }
        }
    }

    @LogInputAndOutput
    override suspend fun retrieve(tenant: String, campaignKey: CampaignKey): CampaignExecutionDetails {
        join()
        return retrieveCampaignsReports(tenant, listOf(campaignKey)).firstOrNull()
            ?: error("No in-memory campaign report found for campaign $campaignKey")
    }

    data class ForcedStatus(val executionStatus: ExecutionStatus, val failureReason: String? = null)
}
