package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.AbstractStep

/**
 * Step to shelve values into the shared state registry.
 *
 * @author Eric Jessé
 *
 * @property sharedStateRegistry the bean to keep and retrieve shared states
 * @property specification the closure generating a map of values to push to the shared registry from the context input
 */
internal class ShelveStep<I>(
    id: StepId,
    private val sharedStateRegistry: SharedStateRegistry,
    private val specification: (input: I) -> Map<String, Any?>
) : AbstractStep<I, I>(id, null) {

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()
        sharedStateRegistry.set(
            specification(input).mapKeys { entry -> SharedStateDefinition(context.minionId, entry.key) })
        context.send(input)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
