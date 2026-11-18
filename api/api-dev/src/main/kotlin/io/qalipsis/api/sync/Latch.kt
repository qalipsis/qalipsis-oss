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
import mu.KotlinLogging

/**
 * A synchronization aid that allows one or more coroutines to wait until
 * a condition is matched.
 *
 * A [Latch] is initialized with a given position.
 * The [await] methods statement until a call to [release], after which
 * all waiting coroutines are released and any subsequent invocations of
 * [await] return immediately.
 *
 * The [Latch] can then be locked again using [lock].
 *
 * @author Eric Jessé
 */
class Latch(var isLocked: Boolean = false, val name: String = "") {

    @Volatile
    private var deferred: CompletableDeferred<Unit>? = null

    init {
        if (isLocked) {
            deferred = CompletableDeferred()
        }
    }

    suspend fun await() {
        deferred?.await()
    }

    /**
     * Releases all the awaiting and future calls to [await].
     */
    suspend fun release() {
        if (isLocked) {
            isLocked = false
            deferred?.complete(Unit)
            deferred = null
            log.trace { "Latch $this is now unlocked" }
        }
    }

    /**
     * Suspends all the future calls to [await].
     */
    suspend fun lock() {
        if (!isLocked) {
            deferred = CompletableDeferred()
            isLocked = true
            log.trace { "Latch $this is now locked" }
        }
    }

    /**
     * Releases all the awaiting calls to [await] and suspends the concurrent and future ones.
     */
    suspend fun releaseAwaiting() {
        val oldDeferred = deferred
        deferred = CompletableDeferred()
        isLocked = true
        oldDeferred?.complete(Unit)
    }

    /**
     * Forces to unlock all the current and future calls to [await].
     */
    fun cancel() {
        isLocked = false
        deferred?.complete(Unit)
        deferred = null
    }

    override fun toString(): String {
        return "Latch(name='${name.takeIf { it.isNotEmpty() } ?: "<No name>"}', isLocked=$isLocked)"
    }

    companion object {

        @JvmStatic
        private val log = KotlinLogging.logger { }

    }
}