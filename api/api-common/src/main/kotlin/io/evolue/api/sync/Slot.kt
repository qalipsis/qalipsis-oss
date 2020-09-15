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
     * Sets the value into the slot and release all the current caller to [get] or [remove].
     */
    suspend fun set(value: T) {
        writeLock.withLock {
            this.value = value
            latch.release()
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
        return writeLock.withLock {
            await()
            val result = value!!
            latch.lock()
            value = null
            result
        }
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