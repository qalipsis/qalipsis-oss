package io.evolue.core.heads.lifetime

/**
 * Interface for objects that prevents the evolue process to exit while their processing is not complete.
 *
 * @author Eric Jessé
 */
interface ProcessBlocker {

    suspend fun join()

}
