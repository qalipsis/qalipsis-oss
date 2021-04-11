package io.qalipsis.core.report

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.annotations.VisibleForTest
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

@Singleton
@Requires(env = ["standalone", "volatile"])
internal class StandaloneInMemoryCampaignStateKeeperImpl(
    private val idGenerator: IdGenerator
) : CampaignStateKeeper {

    private val runningCampaigns = ConcurrentHashMap<CampaignId, ConcurrentHashMap<ScenarioId, RunningCampaign>>()

    override fun start(campaignId: CampaignId, scenarioId: ScenarioId) {
        runningCampaigns.computeIfAbsent(campaignId) { ConcurrentHashMap() }[scenarioId] =
            RunningCampaign(campaignId, scenarioId)
    }

    override fun complete(campaignId: CampaignId, scenarioId: ScenarioId) {
        runningCampaigns[campaignId]!![scenarioId]!!.end = Instant.now()
    }

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

    override fun delete(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, messageId: Any) {
        runningCampaigns[campaignId]!![scenarioId]!!.messages.remove(messageId)
    }

    override fun recordStartedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int) {
        runningCampaigns[campaignId]!![scenarioId]!!.startedMinions.addAndGet(count)
    }

    override fun recordCompletedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int) {
        runningCampaigns[campaignId]!![scenarioId]!!.completedMinions.addAndGet(count)
    }

    override fun recordSuccessfulStepExecution(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        count: Int
    ) {
        runningCampaigns[campaignId]!![scenarioId]!!.successfulStepExecutions.addAndGet(count)
    }

    override fun recordFailedStepExecution(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, count: Int) {
        runningCampaigns[campaignId]!![scenarioId]!!.failedStepExecutions.addAndGet(count)
    }

    override fun report(campaignId: CampaignId): CampaignReport {
        return runningCampaigns[campaignId]?.values?.let { runningCampaigns ->
            val scenariosReports = runningCampaigns.map { runningCampaign ->
                runningCampaign.run {
                    ScenarioReport(
                        campaignId,
                        scenarioId,
                        start,
                        end,
                        startedMinions.get(),
                        completedMinions.get(),
                        0, // FIXME
                        successfulStepExecutions.get(),
                        failedStepExecutions.get(),
                        when {
                            messages.values.any { it.severity == ReportMessageSeverity.ABORT } -> ExecutionStatus.ABORTED
                            messages.values.any { it.severity == ReportMessageSeverity.ERROR } -> ExecutionStatus.FAILED
                            messages.values.any { it.severity == ReportMessageSeverity.WARN } -> ExecutionStatus.WARNING
                            else -> ExecutionStatus.SUCCESSFUL
                        },
                        messages.values.toList()
                    )
                }
            }
            CampaignReport(
                campaignId,
                scenariosReports.minOf(ScenarioReport::start),
                if (scenariosReports.any { it.end == null }) null else scenariosReports.maxOf { it.end!! },
                scenariosReports.sumOf { it.configuredMinionsCount },
                scenariosReports.sumOf { it.executedMinionsCount },
                scenariosReports.sumOf { it.stepsCount },
                scenariosReports.sumOf { it.successfulExecutions },
                scenariosReports.sumOf { it.failedExecutions },
                when {
                    scenariosReports.any { it.status == ExecutionStatus.ABORTED } -> ExecutionStatus.ABORTED
                    scenariosReports.any { it.status == ExecutionStatus.FAILED } -> ExecutionStatus.FAILED
                    scenariosReports.any { it.status == ExecutionStatus.WARNING } -> ExecutionStatus.WARNING
                    else -> ExecutionStatus.SUCCESSFUL
                },
                scenariosReports
            )
        } ?: throw IllegalArgumentException("The campaign with ID $campaignId does not exist")
    }

    @VisibleForTest
    data class RunningCampaign(val campaignId: CampaignId, val scenarioId: ScenarioId) {
        val start: Instant = Instant.now()
        val startedMinions = AtomicInteger()
        val completedMinions = AtomicInteger()
        val successfulStepExecutions = AtomicInteger()
        val failedStepExecutions = AtomicInteger()
        var end: Instant? = null
        val messages = linkedMapOf<Any, ReportMessage>()
    }

}
