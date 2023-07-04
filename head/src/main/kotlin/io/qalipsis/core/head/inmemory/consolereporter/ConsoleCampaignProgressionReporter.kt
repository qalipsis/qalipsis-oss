/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.inmemory.consolereporter

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.ConsoleAppender
import com.varabyte.kotter.foundation.runUntilSignal
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.timer.addTimer
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.inject.Singleton
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.STANDALONE]),
    Requires(property = "report.export.console-live.enabled", defaultValue = "false", value = "true")
)
internal class ConsoleCampaignProgressionReporter(
    private val meterRegistry: CampaignMeterRegistry,
    private val consoleMeterReporter: ConsoleMeterReporter
) : CampaignHook, ProcessBlocker {

    private lateinit var campaignState: CampaignState

    private lateinit var campaignCompletion: CompletableFuture<Unit>

    private var consoleAppender: ConsoleAppender<*>? = null

    private val processBlocker = Latch(false)

    override fun getOrder(): Int {
        return super<CampaignHook>.getOrder()
    }

    override suspend fun join() {
        processBlocker.await()
    }

    override fun cancel() {
        processBlocker.cancel()
    }

    override suspend fun preCreate(campaignConfiguration: CampaignConfiguration, runningCampaign: RunningCampaign) {
        // Initializes the states of the campaign and scenarios.
        campaignState = CampaignState(runningCampaign.key)
        runningCampaign.scenarios.forEach { (scenarioName, scenarioConfiguration) ->
            campaignState.progressionState.scheduledMinions.addAndGet(scenarioConfiguration.minionsCount)
            campaignState.scenarios[scenarioName] = ScenarioState(scenarioName).also {
                it.progressionState.scheduledMinions.set(scenarioConfiguration.minionsCount)
            }
        }

        consoleAppender = disableLogbackConsoleAppender()
        val consoleRenderer = ConsoleRenderer(campaignState, meterRegistry, consoleMeterReporter)
        campaignCompletion = CompletableFuture()

        thread(name = "console-live-reporter", isDaemon = false) {
            session(clearTerminal = true) {
                section {
                    consoleRenderer.render(this)
                }.runUntilSignal {
                    addTimer(1.seconds, repeat = true) {
                        rerender()
                        if (campaignCompletion.isDone) {
                            log.info { "Stopping the rendering of the live in the console" }
                            repeat = false
                            signal()
                        }
                    }
                }
            }
        }
    }

    /**
     * Disables the console appender to avoid having all the exceptions listed in the console.
     */
    private fun disableLogbackConsoleAppender(): ConsoleAppender<*>? {
        val loggerContext = (LoggerFactory.getILoggerFactory() as LoggerContext)
        val consoleAppender = loggerContext.getLogger("ROOT").getAppender("console") as? ConsoleAppender<*>
        consoleAppender?.stop()
        return consoleAppender
    }

    suspend fun report() {
        delay(2000)
        val consoleRenderer = ConsoleRenderer(campaignState, meterRegistry, consoleMeterReporter)
        session(clearTerminal = true) {
            section {
                consoleRenderer.render(this)
            }.runUntilSignal {
                addTimer(1.seconds, repeat = true) {
                    rerender()
                    if (campaignCompletion.isDone) {
                        log.info { "Stopping the rendering of the live in the console" }
                        repeat = false
                        signal()
                    }
                }
            }
        }
        consoleAppender?.start()
        processBlocker.cancel()
    }

    override suspend fun preSchedule(
        campaignConfiguration: CampaignConfiguration,
        runningCampaign: RunningCampaign
    ) = Unit

    override suspend fun preStart(runningCampaign: RunningCampaign) {
        if (runningCampaign.hardTimeoutSec > 0) {
            campaignState.timeout.set(Instant.ofEpochSecond(runningCampaign.hardTimeoutSec))
        } else if (runningCampaign.softTimeoutSec > 0) {
            campaignState.timeout.set(Instant.ofEpochSecond(runningCampaign.softTimeoutSec))
        }
    }

    fun start(scenarioName: ScenarioName) {
        campaignState.start.compareAndSet(null, Instant.now())
    }

    fun recordStartedMinion(scenarioName: ScenarioName, count: Int) {
        campaignState.progressionState.startedMinions.addAndGet(count)
        campaignState.scenarios[scenarioName]!!.progressionState.startedMinions.addAndGet(count)
    }

    fun recordCompletedMinion(scenarioName: ScenarioName, count: Int) {
        campaignState.progressionState.completedMinions.addAndGet(count)
        campaignState.scenarios[scenarioName]!!.progressionState.completedMinions.addAndGet(count)
    }

    suspend fun recordSuccessfulStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName
    ) {
        if (!stepName.startsWith("__")) { // If the step is not technical.
            campaignState.scenarios[scenarioName]?.also { scenario ->
                scenario.stepInitializationOrder += stepName
                scenario.steps.computeIfAbsent(stepName) { StepState(stepName) }.failedInitialization.set(false)
            }
        }
    }

    suspend fun recordFailedStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        cause: Throwable?
    ) {
        campaignState.scenarios[scenarioName]?.also { scenario ->
            scenario.stepInitializationOrder += stepName
            scenario.failedInitialization.set(true)
            scenario.reportableErrors.incrementAndGet()
            if (!stepName.startsWith("__")) { // If the step is not technical.
                scenario.steps.computeIfAbsent(stepName) { StepState(stepName) }.let { step ->
                    step.failedInitialization.set(true)
                    cause?.message?.let {
                        step.messages["_init_$stepName"] =
                            ExecutionMessage("_init_$stepName", ReportMessageSeverity.ERROR, it)
                    }
                }
            } else {
                cause?.message?.let { scenario.messages.add(ExecutionMessage("", ReportMessageSeverity.ERROR, it)) }
            }
        }
    }

    fun recordSuccessfulStepExecution(
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ) {
        if (!stepName.startsWith("__")) { // If the step is not technical.
            campaignState.progressionState.successfulSteps.addAndGet(count)
            campaignState.scenarios[scenarioName]?.also { scenario ->
                scenario.progressionState.successfulSteps.addAndGet(count)
                scenario.steps.computeIfAbsent(stepName) { StepState(stepName) }.successfulExecutions.addAndGet(
                    count
                )
            }
        }
    }

    fun recordFailedStepExecution(
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int,
        cause: Throwable?
    ) {
        campaignState.scenarios[scenarioName]?.also { scenario ->
            scenario.reportableErrors.addAndGet(count)
            if (!stepName.startsWith("__")) { // If the step is not technical.
                campaignState.progressionState.failedSteps.addAndGet(count)
                scenario.progressionState.failedSteps.addAndGet(count)
                scenario.steps.computeIfAbsent(stepName) { StepState(stepName) }.let { step ->
                    step.failedExecutions.addAndGet(count)
                    cause?.javaClass?.let { causeType ->
                        step.errorsByType.computeIfAbsent(causeType) { AtomicInteger() }.addAndGet(count)
                    }
                }
            } else {
                cause?.javaClass?.let { causeType ->
                    scenario.errorsByType.computeIfAbsent(causeType) { AtomicInteger() }.addAndGet(count)
                }
            }
        }
    }

    fun attachMessage(
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        messageId: String,
        message: String
    ) {
        campaignState.scenarios[scenarioName]?.also { scenario ->
            if (severity == ReportMessageSeverity.ERROR) {
                scenario.reportableErrors.incrementAndGet()
            } else if (severity == ReportMessageSeverity.WARN) {
                scenario.reportableWarnings.incrementAndGet()
            }
            scenario.steps.computeIfAbsent(stepName) { StepState(stepName) }.let { step ->
                step.messages[messageId] = ExecutionMessage(messageId, severity, message)
            }
        }
    }

    fun detachMessage(
        scenarioName: ScenarioName,
        stepName: StepName,
        messageId: Any
    ) {
        campaignState.scenarios[scenarioName]?.also { scenario ->
            scenario.steps[stepName]?.messages?.remove("$messageId")?.let { message ->
                if (message.severity == ReportMessageSeverity.ERROR) {
                    scenario.reportableErrors.decrementAndGet()
                } else if (message.severity == ReportMessageSeverity.WARN) {
                    scenario.reportableWarnings.decrementAndGet()
                }
            }
        }
    }

    fun complete(scenarioName: ScenarioName) {
        campaignState.scenarios[scenarioName]!!.end.set(Instant.now())
    }

    fun stop() {
        campaignState.end.set(Instant.now())
        campaignCompletion.complete(Unit)
    }


    private companion object {

        val log = logger()
    }
}