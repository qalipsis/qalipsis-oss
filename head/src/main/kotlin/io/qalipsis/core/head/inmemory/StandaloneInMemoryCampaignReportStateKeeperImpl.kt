package io.qalipsis.core.head.inmemory

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId
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
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.report.toCampaignReport
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.inject.Singleton
import org.slf4j.event.Level
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PreDestroy

@Singleton
@Requires(env = [STANDALONE])
internal class StandaloneInMemoryCampaignReportStateKeeperImpl(
    private val idGenerator: IdGenerator
) : CampaignReportStateKeeper, CampaignReportLiveStateRegistry, ProcessBlocker {

    @KTestable
    private val scenarioStates = ConcurrentHashMap<ScenarioId, InMemoryScenarioReportingExecutionState>()

    /**
     * Counter for the running scenarios to block the process until the campaign is completed.
     */
    private val runningCampaignLatch = Latch(true)

    override fun getOrder() = Int.MAX_VALUE

    override suspend fun join() {
        runningCampaignLatch.await()
    }

    @PreDestroy
    override fun cancel() {
        runningCampaignLatch.cancel()
    }

    @LogInput(Level.DEBUG)
    override suspend fun start(campaignId: CampaignId, scenarioId: ScenarioId) {
        scenarioStates[scenarioId] = InMemoryScenarioReportingExecutionState(scenarioId)
    }

    @LogInput(Level.DEBUG)
    override suspend fun complete(campaignId: CampaignId, scenarioId: ScenarioId) {
        scenarioStates[scenarioId]!!.end = Instant.now()
    }

    @LogInput(Level.DEBUG)
    override suspend fun complete(campaignId: CampaignId) {
        runningCampaignLatch.cancel()
    }

    @LogInput(Level.DEBUG)
    override suspend fun abort(campaignId: CampaignId) {
        val abortTimestamp = Instant.now()
        scenarioStates.filterValues { state -> state.end == null && state.abort == null }
            .forEach { (_, state) ->
                state.abort = abortTimestamp
                state.status = ExecutionStatus.ABORTED
            }
        runningCampaignLatch.cancel()
    }

    @LogInputAndOutput
    override suspend fun put(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        severity: ReportMessageSeverity,
        messageId: Any?,
        message: String
    ): Any {
        return (messageId?.toString()?.takeIf(String::isNotBlank) ?: idGenerator.short()).also { id ->
            scenarioStates[scenarioId]!!.messages[id] = ReportMessage(stepId, id, severity, message)
        }
    }

    override suspend fun clear(campaignId: CampaignId) {
        scenarioStates.clear()
        runningCampaignLatch.lock()
    }

    @LogInput
    override suspend fun delete(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, messageId: Any) {
        scenarioStates[scenarioId]!!.messages.remove(messageId)
    }

    @LogInput
    override suspend fun recordStartedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int): Long {
        return scenarioStates[scenarioId]!!.startedMinionsCounter.addAndGet(count).toLong()
    }

    @LogInput
    override suspend fun recordCompletedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int): Long {
        return scenarioStates[scenarioId]!!.completedMinionsCounter.addAndGet(count).toLong()
    }

    @LogInput
    override suspend fun recordSuccessfulStepExecution(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        count: Int
    ): Long {
        return scenarioStates[scenarioId]!!.successfulStepExecutionsCounter.addAndGet(count).toLong()
    }

    @LogInput
    override suspend fun recordFailedStepExecution(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        count: Int
    ): Long {
        return scenarioStates[scenarioId]!!.failedStepExecutionsCounter.addAndGet(count).toLong()
    }

    @LogInputAndOutput
    override suspend fun report(campaignId: CampaignId): CampaignReport {
        join()
        return scenarioStates.map { (_, runningScenarioCampaign) ->
            runningScenarioCampaign.toReport(campaignId)
        }.toCampaignReport()
    }

}
