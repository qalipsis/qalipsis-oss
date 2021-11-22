package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.AbstractStep

/**
 * Step to unshelve values from the shared state registry.
 *
 * @author Eric Jessé
 *
 * @property sharedStateRegistry the bean to keep and retrieve shared states
 * @property names the keys of all the values to fetch from the registry
 * @property delete when set to true, the values are removed from the registry after use
 */
internal class UnshelveStep<I>(
    id: StepId,
    private val sharedStateRegistry: SharedStateRegistry,
    private val names: List<String>,
    private val delete: Boolean
) : AbstractStep<I, Pair<I, Map<String, Any?>>>(id, null) {

    override suspend fun execute(context: StepContext<I, Pair<I, Map<String, Any?>>>) {
        val input = context.receive()
        val definitions = names.map { name -> SharedStateDefinition(context.minionId, name) }
        val values = if (delete) sharedStateRegistry.remove(definitions) else sharedStateRegistry.get(definitions)
        context.send(input to values)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}

/**
 * Step to unshelve a single value from the shared state registry.
 *
 * @author Eric Jessé
 *
 * @property sharedStateRegistry the bean to keep and retrieve shared states
 * @property name the key of the value to fetch from the registry
 * @property delete when set to true, the value is removed from the registry after use
 */
internal class SingularUnshelveStep<I, O>(
    id: StepId,
    private val sharedStateRegistry: SharedStateRegistry,
    private val name: String,
    private val delete: Boolean
) : AbstractStep<I, Pair<I, O?>>(id, null) {

    override suspend fun execute(context: StepContext<I, Pair<I, O?>>) {
        val input = context.receive()
        val definition = SharedStateDefinition(context.minionId, name)
        val value = if (delete) sharedStateRegistry.remove<O>(definition) else sharedStateRegistry.get<O>(definition)
        context.send(input to value)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
