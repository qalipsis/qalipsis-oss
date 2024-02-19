/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

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
            retries: Int = 10,
            healthCheck: suspend (T) -> Boolean = { true },
            cleaner: suspend (T) -> Unit = { },
            factory: suspend () -> T
        ): Pool<T> = FixedPool(
            size,
            coroutineContext,
            checkOnAcquire,
            checkOnRelease,
            retries,
            healthCheck,
            cleaner,
            factory
        ).apply { init() }
    }
}
