package io.qalipsis.core.persistence

/**
 *
 * @author Eric Jessé
 */
interface Entity<ID : Any> {
    var id: ID
}
