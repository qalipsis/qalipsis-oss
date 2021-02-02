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
 * Setting a new value when one is already present will throw an error.
 *
 * @author Eric Jessé
 */
class ImmutableSlot<T>(private var value: T? = null) {

    private var latch: Latch? = Latch(value == null)

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
     * Sets the value into the slot and release all the current caller to [get].
     */
    suspend fun set(value: T) {
        if (isPresent()) {
            error("A value is already present")
        }
        writeLock.withLock {
            this.value = value
            latch?.release()
            latch = null
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
     * If not value is currently, suspend the call until the latch is released.
     */
    private suspend fun await() {
        if (isEmpty()) {
            latch?.await()
        }
    }

}
