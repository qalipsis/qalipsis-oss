package io.evolue.api.states

import io.evolue.api.steps.Step

/**
 * Interface to implement for the [Step]s that need to mutate or get a shared state.
 *
 * @author Eric Jessé
 */
interface SharedStateAwareStep<I, O> : Step<I, O> {

    fun set(registry: SharedStateRegistry)

}