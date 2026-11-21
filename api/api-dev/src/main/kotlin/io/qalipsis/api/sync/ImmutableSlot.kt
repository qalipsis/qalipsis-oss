/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
     *
     * When the value is already present, it is returned immediately and no timer is scheduled.
     */
    suspend fun get(timeout: Duration): T = if (deferred.isCompleted) {
        deferred.await()
    } else {
        withTimeout(timeout.toMillis()) {
            deferred.await()
        }
    }

    override fun toString(): String {
        return "ImmutableSlot(value=${if (isPresent()) deferred.getCompleted() else null})"
    }

}
