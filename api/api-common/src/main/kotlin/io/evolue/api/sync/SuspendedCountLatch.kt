package io.evolue.api.sync

import io.evolue.api.logging.LoggerHelper.logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onReceiveOrNull
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * A synchronization aid that allows one or more coroutines to wait until
 * a set of operations being performed in other coroutines completes.
 *
 *
 * A [SuspendedCountLatch] is initialized with a given _count_.
 * The [await] methods block until the current count reaches
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
    private var initialCount: Long,
    private val allowsNegative: Boolean = false,
    private val onReleaseAction: suspend (() -> Unit) = {}) {

    private var syncFlag = Channel<Unit>(1)

    private val mutex = Mutex()

    private var count = AtomicLong(initialCount)

    val onRelease
        get() = syncFlag.onReceiveOrNull()

    init {
        require(initialCount >= 0) { "count < 0" }
        // If the count is already 0, the sync flag is released.
        if (initialCount == 0L) {
            syncFlag.close()
        }
    }

    suspend fun await() {
        syncFlag.receiveOrNull()
    }

    suspend fun release() {
        mutex.withLock {
            count.set(0)
            syncFlag.close()
        }
    }

    suspend fun reset() {
        mutex.withLock {
            logger().trace("Resetting...")
            count.set(initialCount)
            resetSyncFlag()
        }
    }

    suspend fun increment(value: Long = 1) {
        mutex.withLock {
            count.addAndGet(value)
            logger().trace("Count is now $count")
            // When the count gets from 0 to 1, the sync flag is recreated.
            if (count.get() == 1L) {
                resetSyncFlag()
            }
        }
    }

    fun blockingIncrement(value: Long = 1) {
        runBlocking {
            increment(value)
        }
    }

    suspend fun decrement(value: Long = 1) {
        mutex.withLock {
            require(allowsNegative || value <= count.get()) { "value > ${count.get()}" }
            count.addAndGet(-value)
            logger().trace("Count is now $count")
            if (count.get() == 0L) {
                onReleaseAction()
                syncFlag.close()
            }
        }
    }

    fun blockingDecrement(value: Long = 1) {
        runBlocking {
            decrement(value)
        }
    }

    fun get() = count.get()

    fun isSuspended(): Boolean {
        logger().trace("Is suspended?")
        return count.get() > 0
    }

    private fun resetSyncFlag() {
        if (syncFlag.isClosedForReceive) {
            syncFlag = Channel(1)
        }
    }

}
