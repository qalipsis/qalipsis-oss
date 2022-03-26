package io.qalipsis.core.factory.communication

import io.micronaut.core.order.Ordered
import io.qalipsis.core.directives.Directive

/**
 * A [DirectiveListener] receives [Directive]s for processing.
 *
 * @author Eric Jessé
 */
interface DirectiveListener<D : Directive> : Ordered {

    /**
     * Returns {@code true} when the listener can work with the directive.
     */
    fun accept(directive: Directive): Boolean

    /**
     * Processes the [Directive].
     */
    suspend fun notify(directive: D)

}
