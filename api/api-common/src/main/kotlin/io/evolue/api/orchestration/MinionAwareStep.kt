package io.evolue.api.orchestration

import io.evolue.api.steps.Step

/**
 * Interface to implement for the [Step]s that need to access to the minions instances.
 *
 * @author Eric Jessé
 */
interface MinionAwareStep<I, O> : Step<I, O> {

    fun set(registry: MinionsRegistry)

}