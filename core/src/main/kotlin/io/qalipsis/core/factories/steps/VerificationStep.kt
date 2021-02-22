package io.qalipsis.core.factories.steps

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep

/**
 * Step to assert data.
 *
 * @author Eric Jessé
 */
internal class VerificationStep<I, O>(
    id: StepId,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    @Suppress("UNCHECKED_CAST") private val assertionBlock: (suspend (input: I) -> O) = { value ->
        value as O
    }
) : AbstractStep<I, O>(id, null) {

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.input.receive()
        try {
            val output = assertionBlock(input)
            meterRegistry.counter("step-${id}-assertion", "status", "success", "minion", context.minionId).increment()
            eventsLogger.info("step-${id}-assertion-success") { context.toEventTags() }
            context.output.send(output)
        } catch (e: Error) {
            context.isExhausted = true
            context.errors.add(StepError(e))
            meterRegistry.counter("step-${id}-assertion", "status", "failure", "minion", context.minionId).increment()
            eventsLogger.warn("step-${id}-assertion-failure") { context.toEventTags() }
        } catch (t: Throwable) {
            context.isExhausted = true
            context.errors.add(StepError(t))
            meterRegistry.counter("step-${id}-assertion", "status", "error", "minion", context.minionId).increment()
            eventsLogger.warn("step-${id}-assertion-error") { context.toEventTags() }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
