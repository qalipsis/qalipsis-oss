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

package io.qalipsis.api.sync

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.Duration

/**
 *
 * A slot is a suspended equivalent implementation slightly comparable to a [java.util.concurrent.atomic.AtomicReference].
 *
 * It suspends the caller until a value is set in the slot. All the callers receive the same value.
 * Setting a new value always overwrite the previous one.
 *
 * @author Eric Jessé
 */
class Slot<T>(private var value: T? = null) {

    private val latch = Latch(value == null)

    private val writeLock = Mutex()

    /**
     *  If a value is present, returns `true`, otherwise `false`.
     */
    fun isPresent() = value != null

    /**
     *  If a value is not present, returns `true`, otherwise `false`.
     */
    fun isEmpty() = !isPresent()

    /**
     * Non-blocking operation returning the value if present or an error otherwise.
     */
    fun forceGet() = requireNotNull(value)

    /**
     * Sets the value into the slot and release all the current caller to [get] or [remove].
     */
    suspend fun set(value: T) {
        writeLock.withLock {
            this.value = value
            latch.release()
        }
    }

    /**
     * Clears the content of the slot.
     */
    suspend fun clear() {
        writeLock.withLock {
            latch.lock()
            this.value = null
        }
    }


    /**
     * Blocking implementation of [set].
     */
    fun offer(value: T) {
        runBlocking {
            set(value)
        }
    }

    /**
     * Returns the value if it exists or suspend the call otherwise.
     */
    suspend fun get(): T {
        await()
        return value!!
    }


    /**
     * Returns the value if it exists or null otherwise.
     */
    fun getOrNull(): T? {
        return value
    }

    /**
     * Returns the value if it exists or suspends until the timeout is reached otherwise.
     * If the timeout is reached before a value is set, a [kotlinx.coroutines.TimeoutCancellationException] is thrown.
     */
    suspend fun get(timeout: Duration): T {
        withTimeout(timeout.toMillis()) {
            await()
        }
        return value!!
    }

    /**
     * Returns and removes the value if it exists or suspend the call otherwise.
     */
    suspend fun remove(): T {
        await()
        val result = value!!
        writeLock.withLock {
            latch.lock()
            value = null
        }
        return result
    }

    /**
     * Returns and removes the value if it exists or suspends until the timeout is reached otherwise.
     * If the timeout is reached before a value is available, a [kotlinx.coroutines.TimeoutCancellationException] is thrown.
     */
    suspend fun remove(timeout: Duration): T {
        return withTimeout(timeout.toMillis()) {
            remove()
        }
    }

    /**
     * If not value is currently, suspend the call until the latch is released.
     */
    private suspend fun await() {
        if (isEmpty()) {
            latch.await()
        }
    }

    override fun toString(): String {
        return "Slot(value=$value)"
    }

}
