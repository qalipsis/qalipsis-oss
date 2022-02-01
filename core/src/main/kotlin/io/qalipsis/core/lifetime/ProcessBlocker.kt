package io.qalipsis.core.lifetime

/**
 * Interface for objects that prevents the QALIPSIS process to exit while their processing is not complete.
 * The implementation should manage the cancellation of the blocking operation with a @PreDestroy operation.
 *
 * @author Eric Jessé
 */
interface ProcessBlocker {

    /**
     * Order of the blocker to verify its completion.
     */
    fun getOrder() = 0

    /**
     * Suspends the caller until the pending operation is complete.
     */
    suspend fun join()

    /**
     * Cancels the pending operation and releases the related resources.
     */
    fun cancel()

}
