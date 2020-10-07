package io.qalipsis.core.heads.lifetime

/**
 * Interface for objects that prevents the qalipsis process to exit while their processing is not complete.
 *
 * @author Eric Jessé
 */
interface ProcessBlocker {

    suspend fun join()

}
