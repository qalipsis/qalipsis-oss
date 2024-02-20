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
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.AbstractStep
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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

    private val assertionFailuresCount = AtomicInteger()

    private lateinit var successMeter: Counter

    private lateinit var failureMeter: Counter

    private lateinit var errorMeter: Counter

    private var started = AtomicBoolean()

    override suspend fun start(context: StepStartStopContext) {
        val tags = context.toMetersTags()
        successMeter = meterRegistry.counter(context.scenarioName, name, "assertion", tags + ("status" to "success"))
        failureMeter = meterRegistry.counter(context.scenarioName, name, "assertion", tags + ("status" to "failure"))
        errorMeter = meterRegistry.counter(context.scenarioName, name, "assertion", tags + ("status" to "error"))
        started.set(true)
        super.start(context)
    }

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.receive()
        try {
            val output = assertionBlock(input)
            successMeter.increment()
            eventsLogger.info("step.assertion.success") { context.toEventTags() }
            context.send(output)
        } catch (e: Error) {
            assertionFailuresCount.incrementAndGet()
            context.isExhausted = true
            context.addError(StepError(e, this.name))
            failureMeter.increment()
            eventsLogger.warn("step.assertion.failure", value = e.message) { context.toEventTags() }
        } catch (t: Throwable) {
            context.isExhausted = true
            context.addError(StepError(t, this.name))
            errorMeter.increment()
            eventsLogger.warn("step.assertion.error", value = t.message) { context.toEventTags() }
        }
    }

    override suspend fun stop(context: StepStartStopContext) {
        if (started.compareAndExchange(true, false) && assertionFailuresCount.get() > 0) {
            reportLiveStateRegistry.put(
                context.campaignKey,
                context.scenarioName,
                this.name,
                ReportMessageSeverity.ERROR,
                "Assertion failure(s): ${assertionFailuresCount.get()} (See the details in the events)"
            )
        }
        super.stop(context)
    }
}
