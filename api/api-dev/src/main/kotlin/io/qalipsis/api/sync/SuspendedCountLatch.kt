package io.qalipsis.api.sync

import io.qalipsis.api.logging.LoggerHelper.logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onReceiveOrNull
import kotlinx.coroutines.channels.receiveOrNull
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
    private val onReleaseAction: suspend (() -> Unit) = {}) {

    private var activityFlag = Channel<Unit>(1)

    private var syncFlag = Channel<Unit>(1)

    private val mutex = Mutex()

    private var count = AtomicLong(initialCount)

    @ExperimentalCoroutinesApi
    val onRelease
        get() = syncFlag.onReceiveOrNull()

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
        activityFlag.receiveOrNull()
        return this
    }

    /**
     * Awaits for the counter to be 0.
     */
    suspend fun await() {
        syncFlag.receiveOrNull()
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
     * Releases all current and future calls to [await] and sets the counter to 0.
     */
    fun release() {
        count.set(0)
        syncFlag.close()
    }

    /**
     * Resets the counter to its initial count provided in the constructor.
     *
     * @return the value of the counter after the reset.
     */
    suspend fun reset(): Long = mutex.withLock {
        logger().trace("Resetting...")
        count.set(initialCount)
        resetFlags()
        count.get()
    }

    /**
     * Blocking implementation of [reset].
     */
    fun blockingReset(): Long = runBlocking { reset() }

    suspend fun increment(value: Long = 1): Long = mutex.withLock {
        count.addAndGet(value)
        logger().trace("Count is now $count")
        activityFlag.close()
        // When the count gets from 0 to 1, the sync flag is recreated.
        if (count.get() == 1L) {
            resetFlags()
        }
        count.get()
    }

    fun blockingIncrement(value: Long = 1): Long = runBlocking {
        increment(value)
    }

    suspend fun decrement(value: Long = 1): Long = mutex.withLock {
        require(allowsNegative || value <= count.get()) { "value > ${count.get()}" }
        count.addAndGet(-value)
        logger().trace("Count is now $count")
        activityFlag.close()
        if (count.get() == 0L) {
            onReleaseAction()
            syncFlag.close()
        }
        count.get()
    }

    fun blockingDecrement(value: Long = 1): Long = runBlocking {
        decrement(value)
    }

    fun get() = count.get()

    fun isSuspended(): Boolean {
        logger().trace("Is suspended?")
        return count.get() > 0
    }

    private fun resetFlags() {
        if (syncFlag.isClosedForReceive) {
            syncFlag = Channel(1)
        }
        if (activityFlag.isClosedForReceive) {
            activityFlag = Channel(1)
        }
    }

}
