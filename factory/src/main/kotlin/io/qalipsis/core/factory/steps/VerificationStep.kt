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

import io.micrometer.core.instrument.Counter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.AbstractStep
import java.util.concurrent.atomic.AtomicLong

/**
 * Step to assert data.
 *
 * @author Eric Jessé
 */
internal class VerificationStep<I, O>(
    id: StepName,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: CampaignMeterRegistry,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
    @Suppress("UNCHECKED_CAST") private val assertionBlock: (suspend (input: I) -> O) = { value ->
        value as O
    }
) : AbstractStep<I, O>(id, null) {

    private val successCount = AtomicLong()

    private val failureCount = AtomicLong()

    private val errorCount = AtomicLong()

    private lateinit var successMeter: Counter

    private lateinit var failureMeter: Counter

    private lateinit var errorMeter: Counter

    override suspend fun start(context: StepStartStopContext) {
        val tags = context.toMetersTags()
        successMeter = meterRegistry.counter("step-${name}-assertion", tags.and("status", "success"))
        failureMeter = meterRegistry.counter("step-${name}-assertion", tags.and("status", "failure"))
        errorMeter = meterRegistry.counter("step-${name}-assertion", tags.and("status", "error"))
        super.start(context)
    }

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.receive()
        try {
            val output = assertionBlock(input)
            successCount.incrementAndGet()
            successMeter.increment()
            eventsLogger.info("step.assertion.success") { context.toEventTags() }
            context.send(output)
        } catch (e: Error) {
            failureCount.incrementAndGet()
            context.isExhausted = true
            context.addError(StepError(e, this.name))
            failureMeter.increment()
            eventsLogger.warn("step.assertion.failure", value = e.message) { context.toEventTags() }
        } catch (t: Throwable) {
            errorCount.incrementAndGet()
            context.isExhausted = true
            context.addError(StepError(t, this.name))
            errorMeter.increment()
            eventsLogger.warn("step.assertion.error", value = t.message) { context.toEventTags() }
        }
    }


    override suspend fun stop(context: StepStartStopContext) {
        val result =
            """"Success: ${successCount.get()}, Failures (verification errors): ${failureCount.get()}, Errors (execution errors): ${errorCount.get()}""""
        val severity = if (failureCount.get() > 0 || errorCount.get() > 0) {
            ReportMessageSeverity.ERROR
        } else {
            ReportMessageSeverity.INFO
        }
        reportLiveStateRegistry.put(context.campaignKey, context.scenarioName, this.name, severity, result)
        log.info { "Stopping the verification step ${this.name} for the campaign ${context.campaignKey}: $result" }
        super.stop(context)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
