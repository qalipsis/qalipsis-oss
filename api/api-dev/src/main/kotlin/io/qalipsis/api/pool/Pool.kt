package io.qalipsis.api.pool

import io.qalipsis.api.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Interface for a non-blocking pool of items.
 *
 * @author Eric Jessé
 */
interface Pool<T : io.qalipsis.api.io.Closeable> {

    /**
     * Closes the pool and all items in it.
     */
    suspend fun close()

    /**
     * Acquires a new item from the pool. The Item has to be released to the pool or closed once used.
     *
     * @see [release]
     */
    suspend fun acquire(): T

    /**
     * After use, sets an item back to the pool.
     *
     * @see [acquire]
     */
    suspend fun release(item: T)

    /**
     * Executes [block] using an item from the pool, without having to set it back to the pool.
     */
    suspend fun <R> withPoolItem(block: suspend (T) -> R): R

    /**
     * Suspends the caller until the pool in initialized.
     */
    suspend fun awaitReadiness(): Pool<T>

    companion object {

        suspend fun <T : Closeable> fixed(
            size: Int,
            coroutineContext: CoroutineContext = Dispatchers.Default,
            checkOnAcquire: Boolean = false,
            checkOnRelease: Boolean = false,
            healthCheck: suspend (T) -> Boolean = { true },
            cleaner: suspend (T) -> Unit = { },
            factory: suspend () -> T
        ): Pool<T> = FixedPool(
            size,
            coroutineContext,
            checkOnAcquire,
            checkOnRelease,
            healthCheck,
            cleaner,
            factory
        ).apply { init() }
    }
}
