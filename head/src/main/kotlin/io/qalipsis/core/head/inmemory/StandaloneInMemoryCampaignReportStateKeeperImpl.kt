package io.qalipsis.core.head.inmemory

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignName
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
    private val scenarioStates = ConcurrentHashMap<ScenarioName, InMemoryScenarioReportingExecutionState>()

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
    override suspend fun start(campaignName: CampaignName, scenarioName: ScenarioName) {
        scenarioStates[scenarioName] = InMemoryScenarioReportingExecutionState(scenarioName)
    }

    @LogInput(Level.DEBUG)
    override suspend fun complete(campaignName: CampaignName, scenarioName: ScenarioName) {
        scenarioStates[scenarioName]!!.end = Instant.now()
    }

    @LogInput(Level.DEBUG)
    override suspend fun complete(campaignName: CampaignName) {
        runningCampaignLatch.cancel()
    }

    @LogInput(Level.DEBUG)
    override suspend fun abort(campaignName: CampaignName) {
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
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        messageId: Any?,
        message: String
    ): Any {
        return (messageId?.toString()?.takeIf(String::isNotBlank) ?: idGenerator.short()).also { id ->
            scenarioStates[scenarioName]!!.messages[id] = ReportMessage(stepName, id, severity, message)
        }
    }

    override suspend fun clear(campaignName: CampaignName) {
        scenarioStates.clear()
        runningCampaignLatch.lock()
    }

    @LogInput
    override suspend fun delete(
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        stepName: StepName,
        messageId: Any
    ) {
        scenarioStates[scenarioName]!!.messages.remove(messageId)
    }

    @LogInput
    override suspend fun recordStartedMinion(campaignName: CampaignName, scenarioName: ScenarioName, count: Int): Long {
        return scenarioStates[scenarioName]!!.startedMinionsCounter.addAndGet(count).toLong()
    }

    @LogInput
    override suspend fun recordCompletedMinion(
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        count: Int
    ): Long {
        return scenarioStates[scenarioName]!!.completedMinionsCounter.addAndGet(count).toLong()
    }

    @LogInput
    override suspend fun recordSuccessfulStepExecution(
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ): Long {
        return scenarioStates[scenarioName]!!.successfulStepExecutionsCounter.addAndGet(count).toLong()
    }

    @LogInput
    override suspend fun recordFailedStepExecution(
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ): Long {
        return scenarioStates[scenarioName]!!.failedStepExecutionsCounter.addAndGet(count).toLong()
    }

    @LogInputAndOutput
    override suspend fun report(campaignName: CampaignName): CampaignReport {
        join()
        return scenarioStates.map { (_, runningScenarioCampaign) ->
            runningScenarioCampaign.toReport(campaignName)
        }.toCampaignReport()
    }

}
