package io.evolue.api.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
     * Returns the value if it exists or suspend the call otherwise.
     */
    suspend fun get(): T {
        await()
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
     * If not value is currently, suspend the call until the latch is released.
     */
    private suspend fun await() {
        if (isEmpty()) {
            latch.await()
        }
    }

}
