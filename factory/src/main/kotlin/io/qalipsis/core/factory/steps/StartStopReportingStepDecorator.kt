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
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import io.qalipsis.core.exceptions.StepExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Duration

/**
 * Decorator of a step, that records the successes and errors of the [decorated] step and reports the
 * failures to the [reportLiveStateRegistry].
 *
 * @author Eric Jessé
 */
internal class StartStopReportingStepDecorator<I, O>(
    override val decorated: Step<I, O>,
    private val eventsLogger: EventsLogger,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
    private val stepStartTimeout: Duration = Duration.ofSeconds(30)
) : Step<I, O>, StepExecutor, StepDecorator<I, O> {

    override val name: StepName
        get() = decorated.name

    override var retryPolicy: RetryPolicy? = decorated.retryPolicy

    override val next = decorated.next

    private lateinit var eventTags: Map<String, String>

    override suspend fun start(context: StepStartStopContext) {
        try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(stepStartTimeout.toMillis()) {
                    decorated.start(context)
                }
            }
            eventTags = context.toEventTags()
        } catch (t: Throwable) {
            reportLiveStateRegistry.recordFailedStepInitialization(
                context.campaignKey,
                context.scenarioName,
                decorated.name,
                t
            )
            throw t
        }
        super<StepDecorator>.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        super<StepDecorator>.stop(context)
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        try {
            @Suppress("UNCHECKED_CAST")
            log.trace { "Performing the execution of the decorated..." }
            executeStep(minion, decorated as Step<Any?, Any?>, context as StepContext<Any?, Any?>)
            log.trace { "Step completed with success" }
        } catch (t: Throwable) {
            val cause = getFailureCause(t, context, decorated)
            eventsLogger.warn("scenario.operation.failed", cause, tags = eventTags)
            throw t
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
