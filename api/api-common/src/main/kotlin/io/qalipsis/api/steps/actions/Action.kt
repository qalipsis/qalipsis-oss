package io.qalipsis.api.steps.actions

/**
 * Interface of action to be performed in order to create load on a target system to test.
 *
 * An action can be a network request, a write operation on a file system, a production of a message...
 *
 * @author Eric Jessé
 */
interface Action {

    suspend fun execute()

}