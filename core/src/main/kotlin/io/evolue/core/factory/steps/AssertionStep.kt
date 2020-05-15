package io.evolue.core.factory.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepError
import io.evolue.api.context.StepId
import io.evolue.api.events.EventLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.AbstractStep
import io.micrometer.core.instrument.MeterRegistry

/**
 * Step to assert data.
 *
 * @author Eric Jessé
 */
class AssertionStep<I, O>(
    id: StepId,
    private val eventLogger: EventLogger,
    private val meterRegistry: MeterRegistry,
    private val assertionBlock: (suspend (input: I) -> O) = { value -> value as O }
) : AbstractStep<I, O>(id, null) {

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.input.receive()
        try {
            val output = assertionBlock(input)
            meterRegistry.counter("step-${id}-assertion", "status", "success", "minion", context.minionId).increment()
            eventLogger.info("step-${id}-assertion-success") { context.toEventTagsMap() }
            context.output.send(output)
        } catch (e: Error) {
            context.exhausted = true
            context.errors.add(StepError(e))
            meterRegistry.counter("step-${id}-assertion", "status", "failure", "minion", context.minionId).increment()
            eventLogger.warn("step-${id}-assertion-failure") { context.toEventTagsMap() }
        } catch (t: Throwable) {
            context.exhausted = true
            context.errors.add(StepError(t))
            meterRegistry.counter("step-${id}-assertion", "status", "error", "minion", context.minionId).increment()
            eventLogger.warn("step-${id}-assertion-error") { context.toEventTagsMap() }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}