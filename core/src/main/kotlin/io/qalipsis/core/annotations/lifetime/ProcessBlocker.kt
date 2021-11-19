package io.qalipsis.core.annotations.lifetime

/**
 * Interface for objects that prevents the QALIPSIS process to exit while their processing is not complete.
 *
 * @author Eric Jessé
 */
interface ProcessBlocker {

    /**
     * Order of the blocker to verify its completion.
     */
    fun getOrder() = 0

    suspend fun join()

}
