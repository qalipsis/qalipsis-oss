/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.pool

import io.qalipsis.api.coroutines.contextualLaunch
import io.qalipsis.api.io.Closeable
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import kotlin.coroutines.CoroutineContext

/**
 * Suspended pool of fixed size.
 *
 * When an health check is performed and fails, a new instance is created and provided
 * to the caller of the pool.
 *
 * A health check can be performed when acquiring or releasing the items to verify that they are still usable.
 * It can consist of a verification of active connection, buffer availability, threshold...
 *
 * Before the items are released to the pool and before the health check - if enabled at releasing - the object can be
 * cleaned to remove traces of the past use of it: clean buffers, reset counters...
 *
 * @param size the size of the pool.
 * @property checkOnAcquire when true, the health of the item is checked when acquired before it is given to the caller, defaults to false
 * @property checkOnRelease when true, the health of the item is checked just before being set back into the pool, defaults to false
 * @property healthCheck action to perform to verify the health of the items of type [T], defaults to always healthy
 * @property cleaner action to perform before releasing the item to the pool and before the health check, in order to make it reusable, defaults to no operation
 * @property factory creator for a new item for the pool
 * @property retries number of retries to initialize the items
 */
class FixedPool<T : Closeable>(
    size: Int,
    private val coroutineContext: CoroutineContext = Dispatchers.Default,
    private val checkOnAcquire: Boolean = false,
    private val checkOnRelease: Boolean = false,
    private val retries: Int = 10,
    private val healthCheck: suspend (T) -> Boolean = { true },
    private val cleaner: suspend (T) -> Unit = { },
    private val factory: suspend () -> T,
) : Pool<T> {

    /**
     * Available items for next acquisition.
     */
    private val itemPool: Channel<T>

    /**
     * All the items existing into the pool.
     */
    private val items = concurrentList<T>()

    private var open = true

    private val readinessLatch: SuspendedCountLatch

    private val actualSize = size.coerceAtLeast(1)

    private var initialized = false

    init {
        itemPool = Channel(actualSize)
        readinessLatch = SuspendedCountLatch(actualSize.toLong())
    }

    /**
     * Initializes the items of the pool. This function is not thread-safe.
     */
    internal suspend fun init() {
        if (!initialized) {
            runCatching {
                withContext(coroutineContext) {
                    (1..actualSize).map {
                        launch {
                            val item = createItem()
                            if (open) {
                                itemPool.send(item)
                            }
                            readinessLatch.decrement()
                        }
                    }.forEach { it.join() }
                }
            }
            if (readinessLatch.isSuspended()) {
                throw PoolInitializationException()
            }
            initialized = true
        }
    }

    private suspend fun createItem(): T {
        var attempts = retries + 1
        var item: T? = null
        var failure: Exception? = null
        while (item == null && attempts > 0) {
            try {
                item = factory()
                items.add(item)
            } catch (e: Exception) {
                attempts--
                if (attempts == 0) {
                    failure = e
                } else {
                    log.warn(e) { "The item could not be created, but a new attempt will occur: ${e.message}" }
                }
            }
        }
        return item ?: throw PoolItemInitializationException(failure!!)
    }

    override suspend fun awaitReadiness(): FixedPool<T> {
        init()
        readinessLatch.await()
        return this
    }

    override suspend fun acquire(): T {
        log.trace { "Acquiring an item from the pool" }
        val item = itemPool.receiveCatching().getOrNull() ?: throw IllegalStateException("The pool is already closed")
        return if (checkOnAcquire && !isHealthy(item)) {
            log.trace { "The object $item is not healthy and has to be replaced by a new one for the caller" }
            withContext(coroutineContext) {
                contextualLaunch {
                    items.remove(item)
                    kotlin.runCatching { item.close() }
                }
            }
            createItem()
        } else {
            item
        }
    }

    override suspend fun release(item: T) {
        if (open) {
            log.trace { "Returning the object to the pool" }
            withContext(coroutineContext) {
                contextualLaunch {
                    val reusableItem = try {
                        cleaner(item)
                        require(!checkOnRelease || isHealthy(item)) { "object is unhealthy" }
                        item
                    } catch (e: Exception) {
                        log.trace(e) { "The object $item cannot be released to the pool: ${e.message}" }
                        items.remove(item)
                        kotlin.runCatching { item.close() }
                        createItem()
                    }
                    itemPool.send(reusableItem)
                }
            }
        }
    }

    private suspend fun isHealthy(item: T): Boolean {
        return kotlin.runCatching { healthCheck(item) }.getOrNull() ?: false
    }

    override suspend fun <R> withPoolItem(block: suspend (T) -> R): R {
        val item = acquire()
        return try {
            block(item)
        } finally {
            release(item)
        }
    }

    override suspend fun close() {
        open = false
        itemPool.cancel()
        items.forEach { kotlin.runCatching { it.close() } }
        items.clear()
    }

    companion object {

        @JvmStatic
        val log = KotlinLogging.logger { }
    }
}
