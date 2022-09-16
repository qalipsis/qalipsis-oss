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
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import java.util.concurrent.atomic.AtomicLong

/**
 * Decorator of a step, that records the successes and errors of the [decorated] step and reports the state
 * to teh [reportLiveStateRegistry].
 *
 * @author Eric Jessé
 */
internal class ReportingStepDecorator<I, O>(
    override val decorated: Step<I, O>,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry
) : Step<I, O>, StepExecutor, StepDecorator<I, O> {

    override val name: StepName
        get() = decorated.name

    override var retryPolicy: RetryPolicy? = decorated.retryPolicy

    override val next = decorated.next

    private val successCount = AtomicLong()

    private val errorCount = AtomicLong()

    override suspend fun start(context: StepStartStopContext) {
        successCount.set(0)
        errorCount.set(0)
        super<StepDecorator>.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        val result = """"Success: ${successCount.get()}, Execution errors: ${errorCount.get()}""""
        val severity = if (errorCount.get() > 0) {
            ReportMessageSeverity.ERROR
        } else {
            ReportMessageSeverity.INFO
        }
        reportLiveStateRegistry.put(context.campaignKey, context.scenarioName, this.name, severity, result)
        log.info { "Stopping the step ${this.name} for the campaign ${context.campaignKey}: $result" }
        super<StepDecorator>.stop(context)
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        try {
            val exhaustedBefore = context.isExhausted
            decorated.execute(minion, context)

            if (context.isExhausted && !exhaustedBefore) {
                errorCount.incrementAndGet()
            } else {
                successCount.incrementAndGet()
            }
        } catch (t: Throwable) {
            errorCount.incrementAndGet()
            throw t
        }
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // This method should never be called.
        throw NotImplementedError()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
