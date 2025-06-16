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

import io.qalipsis.api.logging.LoggerHelper.logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A synchronization aid that allows one or more coroutines to wait until
 * a set of operations being performed in other coroutines completes.
 *
 *
 * A [SuspendedCountLatch] is initialized with a given _count_.
 * The [await] methods statement until the current count reaches
 * zero due to invocations of the [decrement] method, after which
 * all waiting coroutines are released and any subsequent invocations of
 * [await] return immediately. The count can be reset.
 *
 *
 * [onReleaseAction] supports an optional lambda command
 * that is run once per barrier point, after the last coroutine in the party
 * arrives, but before any coroutines are released.
 * This _barrier action_ is useful
 * for updating shared-state before any of the parties continue.
 *
 * @author Eric Jessé
 */
class SuspendedCountLatch(
    private var initialCount: Long = 0,
    private val allowsNegative: Boolean = false,
    private val onReleaseAction: suspend (SuspendedCountLatch.() -> Unit) = {}
) {

    private var activityFlag = Channel<Unit>(1)

    private var syncFlag = Channel<Unit>(1)

    private val mutex = Mutex()

    private var count = AtomicLong(initialCount)

    val onRelease
        get() = syncFlag.onReceiveCatching

    init {
        // If the count is already 0, the sync flag is released.
        if (initialCount == 0L) {
            syncFlag.close()
        }
    }

    /**
     * Awaits for [increment] or [decrement] to be called at least once.
     */
    suspend fun awaitActivity(): SuspendedCountLatch {
        activityFlag.receiveCatching()
        return this
    }

    /**
     * Awaits for the counter to be 0.
     */
    suspend fun await() {
        syncFlag.receiveCatching()
    }

    /**
     * Awaits for the counter to be 0, until the timeout.
     *
     * @return false if the timeout was reached, true otherwise.
     */
    suspend fun await(timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): Boolean {
        return try {
            withTimeout(unit.toMillis(timeout)) {
                await()
                true
            }
        } catch (e: TimeoutCancellationException) {
            false
        }
    }

    /**
     * Blocking implementation of [release].
     */
    fun blockingRelease() = runBlocking { release() }

    /**
     * Releases all current and future calls to [await] and sets the counter to 0.
     */
    fun cancel() {
        count.set(0)
        activityFlag.close()
        syncFlag.close()
    }

    /**
     * Releases all current and future calls to [await], executes [onReleaseAction] and sets the counter back to 0.
     */
    suspend fun release() {
        releaseSyncFlag()
        count.set(0)
    }

    /**
     * Resets the counter to its initial count provided in the constructor.
     *
     * @return the value of the counter after the reset.
     */
    suspend fun reset(): Long = mutex.withLock {
        log.trace { "Resetting..." }
        count.set(initialCount)
        resetFlag()
        count.get()
    }

    /**
     * Blocking implementation of [reset].
     */
    fun blockingReset(): Long = runBlocking { reset() }

    suspend fun increment(value: Long = 1): Long = mutex.withLock {
        closeActivityFlag()
        val result = count.addAndGet(value)
        // When the count gets from 0 to 1, the sync flag is recreated.
        if (value == 1L && result == 1L) {
            resetFlag()
        }
        result
    }

    fun blockingIncrement(value: Long = 1): Long = runBlocking { increment(value) }

    suspend fun decrement(value: Long = 1): Long {
        return mutex.withLock {
            require(allowsNegative || value <= count.get()) { "value > ${count.get()}" }
            closeActivityFlag()
            val result = count.addAndGet(-value)
            if (result == 0L) {
                releaseSyncFlag()
            }
            result
        }
    }

    private suspend fun releaseSyncFlag() {
        onReleaseAction()
        syncFlag.close()
    }

    fun blockingDecrement(value: Long = 1): Long = runBlocking {
        decrement(value)
    }

    fun get() = count.get()

    fun isSuspended(): Boolean {
        return count.get() != 0L
    }

    private fun resetFlag() {
        if (syncFlag.isClosedForReceive) {
            syncFlag = Channel(1)
        }
    }

    private fun closeActivityFlag() {
        if (!activityFlag.isClosedForReceive) {
            activityFlag.close()
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
