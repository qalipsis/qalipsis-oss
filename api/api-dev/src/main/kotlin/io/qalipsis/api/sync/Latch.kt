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