package io.qalipsis.core.factories.steps

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.AbstractStep
import java.util.concurrent.atomic.AtomicLong

/**
 * Step to assert data.
 *
 * @author Eric Jessé
 */
internal class VerificationStep<I, O>(
    id: StepId,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    private val campaignStateKeeper: CampaignStateKeeper,
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
        successMeter = meterRegistry.counter("step-${id}-assertion", tags.and("status", "success"))
        failureMeter = meterRegistry.counter("step-${id}-assertion", tags.and("status", "failure"))
        errorMeter = meterRegistry.counter("step-${id}-assertion", tags.and("status", "error"))
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
            context.addError(StepError(e, this.id))
            failureMeter.increment()
            eventsLogger.warn("step.assertion.failure", value = e.message) { context.toEventTags() }
        } catch (t: Throwable) {
            errorCount.incrementAndGet()
            context.isExhausted = true
            context.addError(StepError(t, this.id))
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
        campaignStateKeeper.put(context.campaignId, context.scenarioId, this.id, severity, result)
        log.info { "Stopping the verification step ${this.id} for the campaign ${context.campaignId}: $result" }
        super.stop(context)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
