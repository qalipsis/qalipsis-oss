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
import kotlinx.coroutines.withTimeout
import java.time.Duration

/**
 *
 * A slot is a suspended equivalent implementation slightly comparable to a [java.util.concurrent.atomic.AtomicReference].
 *
 * It suspends the caller until a value is set in the slot. All the callers receive the same value.
 * Setting a new value when one is already present will throw an error.
 *
 * @author Eric Jessé
 */
class ImmutableSlot<T>(value: T? = null) {

    private val deferred = CompletableDeferred<T>()

    init {
        if (value != null) deferred.complete(value)
    }

    /**
     *  If a value is present, returns `true`, otherwise `false`.
     */
    fun isPresent() = deferred.isCompleted

    /**
     *  If a value is not present, returns `true`, otherwise `false`.
     */
    fun isEmpty() = !isPresent()

    /**
     * Sets the value into the slot and release all the current caller to [get].
     */
    suspend fun set(value: T) {
        if (!deferred.complete(value)) {
            error("A value is already present")
        }
    }

    /**
     * Blocking implementation of [set].
     */
    fun offer(value: T) {
        if (!deferred.complete(value)) {
            error("A value is already present")
        }
    }

    /**
     * Returns the value if it exists or suspend the call otherwise.
     */
    suspend fun get(): T = deferred.await()

    /**
     * Returns the value if it exists or suspends until the timeout is reached otherwise.
     * If the timeout is reached before a value is set, a [kotlinx.coroutines.TimeoutCancellationException] is thrown.
     */
    suspend fun get(timeout: Duration): T = withTimeout(timeout.toMillis()) {
        deferred.await()
    }

    override fun toString(): String {
        return "ImmutableSlot(value=${if (isPresent()) deferred.getCompleted() else null})"
    }

}
