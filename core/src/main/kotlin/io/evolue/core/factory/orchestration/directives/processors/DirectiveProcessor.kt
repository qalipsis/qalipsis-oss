package io.evolue.core.factory.orchestration.directives.processors

import io.evolue.core.cross.driving.directives.Directive

/**
 * A [DirectiveProcessor] is responsible for executing the directive described in the passed entity.
 *
 * @author Eric Jessé
 */
internal interface DirectiveProcessor<D : Directive> {

    /**
     * Returns {@code true} when the processor can execute the directive.
     */
    fun accept(directive: Directive): Boolean

    /**
     * Execute the directive in the current factory.
     */
    suspend fun process(directive: D)

    /**
     * Defines the order of the processor in the chain. Default is 0.
     */
    fun order() = 0
}
