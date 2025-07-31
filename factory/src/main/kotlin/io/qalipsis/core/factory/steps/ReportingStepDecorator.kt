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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import io.qalipsis.core.exceptions.StepExecutionException
import io.qalipsis.core.factory.orchestration.StepUtils.isHidden
import io.qalipsis.core.factory.orchestration.StepUtils.type
import java.time.Duration

/**
 * Decorator of a step, that records the successes and errors of the [decorated] step and reports the state
 * to the [reportLiveStateRegistry].
 *
 * @author Eric Jessé
 */
class ReportingStepDecorator<I, O>(
    override val decorated: Step<I, O>,
    private val reportErrors: Boolean,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: CampaignMeterRegistry,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry
) : Step<I, O>, StepExecutor, StepDecorator<I, O> {

    override val name: StepName
        get() = decorated.name

    override var retryPolicy: RetryPolicy? = decorated.retryPolicy

    override val next = decorated.next

    private val stepType = decorated.type

    /**
     * Meter storing the number of running steps.
     */
    private lateinit var runningStepsGauge: Gauge

    /**
     * Cumulative counter of the executed steps.
     */
    private lateinit var executedStepCounter: Counter

    /**
     * Timer for successful completion.
     */
    private lateinit var completionTimer: Timer

    /**
     * Timer for failure.
     */
    private lateinit var failureTimer: Timer

    /**
     * Specifies whether the decorated step is visible for the reporting.
     */
    private val isDecoratedVisible = !decorated.isHidden

    override suspend fun start(context: StepStartStopContext) {
        try {
            decorated.start(context)
            reportLiveStateRegistry.recordSuccessfulStepInitialization(
                context.campaignKey,
                context.scenarioName,
                decorated.name
            )
        } catch (t: Throwable) {
            reportLiveStateRegistry.recordFailedStepInitialization(
                context.campaignKey,
                context.scenarioName,
                decorated.name,
                t
            )
            throw t
        }

        runningStepsGauge = meterRegistry.gauge(
            scenarioName = context.scenarioName,
            stepName = context.stepName,
            name = "running-steps",
            tags = mapOf("scenario" to context.scenarioName)
        )
        executedStepCounter = meterRegistry.counter(
            scenarioName = context.scenarioName,
            stepName = context.stepName,
            name = "executed-steps",
            tags = mapOf("scenario" to context.scenarioName))
        completionTimer = meterRegistry.timer(
            scenarioName = context.scenarioName,
            stepName = context.stepName,
            name = "step-execution",
            tags = mapOf("step" to decorated.name, "status" to "completed"))
        failureTimer = meterRegistry.timer(
            scenarioName = context.scenarioName,
            stepName = context.stepName,
            name = "step-execution",
            tags = mapOf("step" to decorated.name, "status" to "failed"))
        super<StepDecorator>.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        super<StepDecorator>.stop(context)
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        log.trace { "Reporting the started execution of the decorated step" }
        context.stepType = stepType

        val runningStepsGauge = if (isDecoratedVisible) {
            eventsLogger.debug("step.execution.started", tagsSupplier = { context.toEventTags() })
            runningStepsGauge.apply { increment() }
        } else null

        val start = System.nanoTime()
        try {
            @Suppress("UNCHECKED_CAST")
            log.trace { "Performing the execution of the decorated..." }
            executeStep(minion, decorated as Step<Any?, Any?>, context as StepContext<Any?, Any?>)
            if (context.isExhausted) {
                log.trace { "Step completed with failure" }
                if (isDecoratedVisible) {
                    log.trace { "Reporting the failed execution of the decorated step" }
                    eventsLogger.warn(
                        "step.execution.failed",
                        context.errors.lastOrNull()?.message,
                        tagsSupplier = { context.toEventTags() })
                    val duration = Duration.ofNanos(System.nanoTime() - start)
                    failureTimer.record(duration)

                    reportLiveStateRegistry.recordFailedStepExecution(
                        context.campaignKey,
                        context.scenarioName,
                        decorated.name
                    )
                } else {
                    eventsLogger.warn(
                        "minion.operation.failed",
                        context.errors.lastOrNull()?.message,
                        tagsSupplier = { context.toEventTags() })
                }
            } else {
                log.trace { "Step completed successfully" }
                if (isDecoratedVisible) {
                    log.trace { "Reporting the successful execution of the decorated step" }
                    Duration.ofNanos(System.nanoTime() - start).let { duration ->
                        eventsLogger.debug("step.execution.complete", tagsSupplier = { context.toEventTags() })
                        completionTimer.record(duration)
                    }
                    reportLiveStateRegistry.recordSuccessfulStepExecution(
                        context.campaignKey,
                        context.scenarioName,
                        decorated.name
                    )
                }
            }
        } catch (t: Throwable) {
            val cause = getFailureCause(t, context, decorated)
            if (isDecoratedVisible) {
                log.trace { "Reporting the failed execution of the decorated step" }
                eventsLogger.warn("step.execution.failed", cause, tagsSupplier = { context.toEventTags() })
                val duration = Duration.ofNanos(System.nanoTime() - start)
                failureTimer.record(duration)

                reportLiveStateRegistry.recordFailedStepExecution(
                    context.campaignKey,
                    context.scenarioName,
                    decorated.name,
                    cause = supplyIf(reportErrors) { t }
                )
            } else {
                eventsLogger.warn("minion.operation.failed", cause, tagsSupplier = { context.toEventTags() })
            }
            throw t
        } finally {
            if (isDecoratedVisible) {
                runningStepsGauge?.decrement()
                executedStepCounter.increment()
            }
        }
    }

    /**
     * Extracts the actual cause of the step execution failure.
     */
    private fun getFailureCause(t: Throwable, stepContext: StepContext<*, *>, step: Step<*, *>) = when {
        t !is StepExecutionException -> {
            stepContext.addError(StepError(t, step.name))
            t
        }

        t.cause != null -> {
            stepContext.addError(StepError(t.cause!!, step.name))
            t.cause!!
        }

        else -> stepContext.errors.lastOrNull()?.message
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // This method should never be called.
        throw NotImplementedError()
    }

    companion object {
        private val log = logger()
    }

}
