package io.evolue.core.head.persistence

/**
 *
 * @author Eric Jessé
 */
interface Entity<ID : Any> {
    var id: ID
}