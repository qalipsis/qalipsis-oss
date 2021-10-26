package io.qalipsis.api.coroutines

import kotlinx.coroutines.CoroutineScope

/**
 * Provider of [CoroutineScope] for the different kinds of operations in QALIPSIS.
 *
 * @author Eric Jessé
 */
interface CoroutineScopeProvider {

    /**
     * Scope for global operations.
     */
    val global: CoroutineScope

    /**
     * Scope for execution of the scenarios.
     */
    val campaign: CoroutineScope

    /**
     * Scope for execution of the network operations.
     */
    val io: CoroutineScope

    /**
     * Scope for the background tasks.
     */
    val background: CoroutineScope

    /**
     * Scope for the orchestration tasks.
     */
    val orchestration: CoroutineScope

    fun close()
}