package io.qalipsis.core.report

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.configuration.ExecutionEnvironments.VOLATILE
import jakarta.inject.Singleton
import org.slf4j.event.Level
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Singleton
@Requires(env = [STANDALONE, VOLATILE])
internal class StandaloneInMemoryCampaignStateKeeperImpl(
    private val idGenerator: IdGenerator
) : CampaignStateKeeper {

    @KTestable
    private val runningCampaigns = ConcurrentHashMap<CampaignId, MutableMap<ScenarioId, RunningCampaign>>()

    private val campaignStatus = ConcurrentHashMap<CampaignId, CampaignReport>()

    /**
     * Counter for the running scenarios to block the reporting until the .
     */
    private val runningScenarioLatch = SuspendedCountLatch()

    @LogInput(Level.DEBUG)
    override fun start(campaignId: CampaignId, scenarioId: ScenarioId) {
        runningCampaigns.computeIfAbsent(campaignId) { ConcurrentHashMap() }[scenarioId] =
            RunningCampaign(campaignId, scenarioId)
        runningScenarioLatch.blockingIncrement()
    }

    @LogInput(Level.DEBUG)
    override fun complete(campaignId: CampaignId, scenarioId: ScenarioId) {
        runningCampaigns[campaignId]!![scenarioId]!!.end = Instant.now()
        runningScenarioLatch.blockingDecrement()
    }

    @LogInput(Level.DEBUG)
    override fun abort(campaignId: CampaignId) {
        campaignStatus[campaignId] = CampaignReport(
            campaignId = campaignId,
            start = Instant.now(),
            end = Instant.now(),
            status = ExecutionStatus.ABORTED
        )
        // Increments at least one to ensure the awaitActivity is released.
        runningScenarioLatch.blockingIncrement()
        runningScenarioLatch.blockingDecrement(1L + (runningCampaigns.get(campaignId)?.values?.size ?: 0))
    }

    @LogInputAndOutput
    override fun put(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        severity: ReportMessageSeverity,
        message: String
    ): Any {
        return idGenerator.short().also { messageId ->
            runningCampaigns[campaignId]!![scenarioId]!!.messages[messageId] =
                ReportMessage(stepId, messageId, severity, message)
        }
    }

    @LogInput
    override fun delete(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, messageId: Any) {
        runningCampaigns[campaignId]!![scenarioId]!!.messages.remove(messageId)
    }

    @LogInput
    override fun recordStartedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int) {
        runningCampaigns[campaignId]!![scenarioId]!!.startedMinions.addAndGet(count)
    }

    @LogInput
    override fun recordCompletedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int) {
        runningCampaigns[campaignId]!![scenarioId]!!.completedMinions.addAndGet(count)
    }

    @LogInput
    override fun recordSuccessfulStepExecution(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        count: Int
    ) {
        runningCampaigns[campaignId]!![scenarioId]!!.successfulStepExecutions.addAndGet(count)
    }

    @LogInput
    override fun recordFailedStepExecution(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, count: Int) {
        runningCampaigns[campaignId]!![scenarioId]!!.failedStepExecutions.addAndGet(count)
    }

    @LogInputAndOutput
    override suspend fun report(campaignId: CampaignId): CampaignReport {
        // Ensure that the details about the scenarios were received and all complete before the report
        // is generated.
        runningScenarioLatch.awaitActivity()
        runningScenarioLatch.await()

        val runningCampaign = runningCampaigns[campaignId]?.values?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("The campaign with ID $campaignId does not exist")

        val forcedReport = campaignStatus[campaignId]
        val scenariosReports = runningCampaign.map { runningScenarioCampaign ->
            runningScenarioCampaign.run {
                ScenarioReport(
                    campaignId,
                    scenarioId,
                    start,
                    end ?: forcedReport?.end ?: Instant.now(),
                    startedMinions.get(),
                    completedMinions.get(),
                    0, // FIXME
                    successfulStepExecutions.get(),
                    failedStepExecutions.get(),
                    when {
                        end == null -> ExecutionStatus.ABORTED
                        messages.values.any { it.severity == ReportMessageSeverity.ABORT } -> ExecutionStatus.ABORTED
                        messages.values.any { it.severity == ReportMessageSeverity.ERROR } -> ExecutionStatus.FAILED
                        messages.values.any { it.severity == ReportMessageSeverity.WARN } -> ExecutionStatus.WARNING
                        else -> ExecutionStatus.SUCCESSFUL
                    },
                    messages.values.toList()
                )
            }
        }

        return CampaignReport(
            campaignId,
            scenariosReports.minOfOrNull(ScenarioReport::start) ?: forcedReport?.start!!,
            forcedReport?.end ?: scenariosReports.maxOf { it.end!! },
            scenariosReports.sumOf { it.configuredMinionsCount },
            scenariosReports.sumOf { it.executedMinionsCount },
            scenariosReports.sumOf { it.stepsCount },
            scenariosReports.sumOf { it.successfulExecutions },
            scenariosReports.sumOf { it.failedExecutions },
            forcedReport?.status ?: when {
                scenariosReports.any { it.status == ExecutionStatus.ABORTED } -> ExecutionStatus.ABORTED
                scenariosReports.any { it.status == ExecutionStatus.FAILED } -> ExecutionStatus.FAILED
                scenariosReports.any { it.status == ExecutionStatus.WARNING } -> ExecutionStatus.WARNING
                else -> ExecutionStatus.SUCCESSFUL
            },
            scenariosReports
        )
    }

    data class RunningCampaign(val campaignId: CampaignId, val scenarioId: ScenarioId) {
        val start: Instant = Instant.now()
        val startedMinions = AtomicInteger()
        val completedMinions = AtomicInteger()
        val successfulStepExecutions = AtomicInteger()
        val failedStepExecutions = AtomicInteger()
        var end: Instant? = null
        val messages = linkedMapOf<Any, ReportMessage>()
    }

    private companion object {

        @JvmStatic
        val log = logger()

    }
}
