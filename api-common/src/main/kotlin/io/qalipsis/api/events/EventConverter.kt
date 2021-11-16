package io.qalipsis.api.events

/**
 * General interface for conversion of [Event]s to a transportable or persistable format.
 *
 * @author Eric Jessé
 */
interface EventConverter<T> {

    fun convert(event: Event): T
}
