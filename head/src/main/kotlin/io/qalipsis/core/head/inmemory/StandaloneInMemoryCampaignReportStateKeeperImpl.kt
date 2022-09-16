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

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.context.CampaignKey
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
import io.qalipsis.core.head.report.CampaignReportProvider
import io.qalipsis.core.head.report.toCampaignReport
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.inject.Singleton
import org.slf4j.event.Level
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PreDestroy

@Singleton
@Requires(env = [STANDALONE])
internal class StandaloneInMemoryCampaignReportStateKeeperImpl(
    private val idGenerator: IdGenerator,
    @Value("\${campaign.report.cache.time-to-live:PT10M}") cacheExpire: Duration
) : CampaignReportStateKeeper, CampaignReportLiveStateRegistry, ProcessBlocker, CampaignReportProvider {

    @KTestable
    private val campaignStates: LoadingCache<CampaignKey, MutableMap<ScenarioName, InMemoryScenarioReportingExecutionState>> =
        Caffeine.newBuilder()
            .expireAfterAccess(cacheExpire)
            .build { ConcurrentHashMap<ScenarioName, InMemoryScenarioReportingExecutionState>() }

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
        runningCampaignLatch.lock()
        campaignStates.get(campaignKey)!!
            .put(scenarioName, InMemoryScenarioReportingExecutionState(scenarioName))
    }

    @LogInput(Level.DEBUG)
    override suspend fun complete(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        campaignStates.get(campaignKey)!!.get(scenarioName)!!.end = Instant.now()
    }

    @LogInput(Level.DEBUG)
    override suspend fun complete(campaignKey: CampaignKey) {
        runningCampaignLatch.cancel()
    }

    @LogInput(Level.DEBUG)
    override suspend fun abort(campaignKey: CampaignKey) {
        val abortTimestamp = Instant.now()
        campaignStates.get(campaignKey)
            ?.filterValues { state -> state.end == null && state.abort == null }
            ?.forEach { (_, state) ->
                state.abort = abortTimestamp
                state.status = ExecutionStatus.ABORTED
            }
        runningCampaignLatch.cancel()
    }

    @LogInputAndOutput
    override suspend fun put(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        messageId: Any?,
        message: String
    ): Any {
        return (messageId?.toString()?.takeIf(String::isNotBlank) ?: idGenerator.short()).also { id ->
            campaignStates.get(campaignKey)!!.get(scenarioName)!!.messages[id] =
                ReportMessage(stepName, id, severity, message)
        }
    }

    override suspend fun clear(campaignKey: CampaignKey) {
        campaignStates.invalidate(campaignKey)
        runningCampaignLatch.lock()
    }

    @LogInput
    override suspend fun delete(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        messageId: Any
    ) {
        campaignStates.get(campaignKey)!!.get(scenarioName)!!.messages.remove(messageId)
    }

    @LogInput
    override suspend fun recordStartedMinion(campaignKey: CampaignKey, scenarioName: ScenarioName, count: Int): Long {
        return campaignStates.get(campaignKey)!!.get(scenarioName)!!.startedMinionsCounter.addAndGet(
            count
        ).toLong()
    }

    @LogInput
    override suspend fun recordCompletedMinion(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        count: Int
    ): Long {
        return campaignStates.get(campaignKey)!!.get(scenarioName)!!.completedMinionsCounter.addAndGet(
            count
        ).toLong()
    }

    @LogInput
    override suspend fun recordSuccessfulStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ): Long {
        return campaignStates.get(campaignKey)!!
            .get(scenarioName)!!.successfulStepExecutionsCounter.addAndGet(count).toLong()
    }

    @LogInput
    override suspend fun recordFailedStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ): Long {
        return campaignStates.get(campaignKey)!!
            .get(scenarioName)!!.failedStepExecutionsCounter.addAndGet(count).toLong()
    }

    @LogInputAndOutput
    override suspend fun generateReport(campaignKey: CampaignKey): CampaignReport? {
        join()
        val scenariosReports = campaignStates.get(campaignKey)
            ?.map { (_, runningScenarioCampaign) -> runningScenarioCampaign.toReport(campaignKey) }
        return scenariosReports?.toCampaignReport()
    }

    @LogInputAndOutput
    override suspend fun retrieveCampaignReport(tenant: String, campaignKey: CampaignKey): CampaignReport {
        join()
        return campaignStates.get(campaignKey)?.map { (_, runningScenarioCampaign) ->
            runningScenarioCampaign.toReport(campaignKey)
        }?.toCampaignReport() ?: throw IllegalArgumentException("No report found for the expected campaign")
    }
}
