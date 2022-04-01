package io.qalipsis.core.persistence

/**
 * Parent interface for entities kept in memory stage.
 *
 * @author Eric Jessé
 */
interface InMemoryEntity<ID : Any> {
    var name: ID
}
