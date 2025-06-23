/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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
package io.qalipsis.core.factory.steps.meter

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.factory.steps.meter.checkers.ValueChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Step to create a [Timer] that allows for specification of failure conditions
 * and evaluation of success and failure of the steps using configured thresholds.
 *
 * @author Francisca Eze
 */
class TimerMeterStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val coroutineScope: CoroutineScope,
    private val campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry,
    private val meterName: String,
    val percentiles: Set<Double>,
    val block: (stepContext: StepContext<I, I>, input: I) -> Duration,
    private val checkers: List<Pair<Timer.() -> Duration, ValueChecker<Duration>>>,
    private val campaignMeterRegistry: CampaignMeterRegistry,
    private val checkPeriod: Duration = Duration.ofMillis(100),
) : AbstractStep<I, I>(id, retryPolicy) {

    private lateinit var meter: Timer

    @KTestable
    private var statusJob: Job? = null

    private var running = false

    private var severity = AtomicReference(ReportMessageSeverity.INFO)

    private var recordedValues = AtomicBoolean(false)

    @KTestable
    private var messageId: String? = null

    override suspend fun start(context: StepStartStopContext) {
        meter = campaignMeterRegistry.timer(
            scenarioName = context.scenarioName,
            stepName = context.stepName,
            name = meterName,
            tags = context.toMetersTags(),
            percentiles = percentiles
        ).report {
            // This block configures the display of the meter values into the live reporter.

            // The first row on the console contains the count of calls, mean and max values.
            display(
                "\u2713 %,.0f",
                severity = { severity.get() },
                row = 0,
                column = 0,
                Timer::count
            )
            display(
                "mean: %,.3f ms",
                severity = { severity.get() },
                row = 0,
                column = 1
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = { severity.get() },
                row = 0,
                column = 2
            ) { this.max(TimeUnit.MILLISECONDS) }

            // Values for percentiles are displayed in order in an additional row,
            // limited to 10 values by security.
            percentiles.sorted().take(10).forEachIndexed { index, percentile ->
                display(
                    "${percentile}%%: %,.3f ms",
                    severity = { severity.get() },
                    row = 1,
                    column = index.toShort()
                ) { this.percentile(percentile, TimeUnit.MILLISECONDS) }
            }
        }

        startStatusCheck(context)
    }

    /**
     * Starts the job that verifies the status of the meter every second, when there are [checkers].
     */
    @KTestable
    private fun startStatusCheck(context: StepStartStopContext) {
        if (checkers.isNotEmpty()) {
            running = true
            statusJob = flow {
                delay(checkPeriod.toMillis())
                while (running) {
                    emit(Unit)
                    delay(checkPeriod.toMillis())
                }
            }.onEach {
                checkState(context)
            }.launchIn(coroutineScope)
        }
    }

    override suspend fun execute(context: StepContext<I, I>) {
        try {
            val input = context.receive()
            val valueToRecord = block(context, input)
            meter.record(valueToRecord)
            recordedValues.set(true)
            context.send(input)
        } catch (e: Exception) {
            log.error(e) { e.message }
            throw e
        }
    }

    override suspend fun stop(context: StepStartStopContext) {
        running = false
        statusJob?.cancelAndJoin()

        // Verifies once more at the end to get the final state.
        checkState(context)
        super.stop(context)
    }

    @KTestable
    private suspend fun checkState(context: StepStartStopContext) {
        val violations = checkers.mapNotNull { (valueExtractor, checker) ->
            checker.check(meter.valueExtractor())
        }
        if (recordedValues.compareAndSet(true, false)) {
            if (violations.isNotEmpty()) {
                severity.set(ReportMessageSeverity.ERROR)
                messageId = campaignReportLiveStateRegistry.put(
                    campaignKey = context.campaignKey,
                    scenarioName = context.scenarioName,
                    stepName = context.stepName,
                    severity = severity.get(),
                    messageId = messageId,
                    message = violations.joinToString("\n") { it.message }
                )
            } else {
                severity.set(ReportMessageSeverity.INFO)
                messageId?.let { messageId ->
                    campaignReportLiveStateRegistry.delete(
                        campaignKey = context.campaignKey,
                        scenarioName = context.scenarioName,
                        stepName = context.stepName,
                        messageId = messageId
                    )
                    this.messageId = null
                }
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}