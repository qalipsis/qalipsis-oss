package io.qalipsis.api.lang

/**
 * Generates random IDs.
 *
 * @author Eric Jessé
 */
interface IdGenerator {

    fun long(): String

    fun short(): String
}
