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

package io.qalipsis.api.sync

import kotlinx.coroutines.CompletableDeferred
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

    private var deferred: CompletableDeferred<Unit>? =
        if (value == null) CompletableDeferred() else null

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
            deferred?.complete(Unit)
            deferred = null
        }
    }

    /**
     * Clears the content of the slot.
     */
    suspend fun clear() {
        writeLock.withLock {
            value = null
            deferred = CompletableDeferred()
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
        deferred?.await()
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
            deferred?.await()
        }
        return value!!
    }

    /**
     * Returns and removes the value if it exists or suspend the call otherwise.
     */
    suspend fun remove(): T {
        deferred?.await()
        val result = value!!
        writeLock.withLock {
            value = null
            deferred = CompletableDeferred()
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

    override fun toString(): String {
        return "Slot(value=$value)"
    }

}
