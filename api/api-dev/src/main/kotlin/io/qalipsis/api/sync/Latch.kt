package io.qalipsis.api.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
class Latch(var isLocked: Boolean) {

    private var syncFlag: Channel<Unit>? = null

    private val mutex = Mutex()

    init {
        if (isLocked) {
            syncFlag = Channel(Channel.RENDEZVOUS)
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun await() {
        if (isLocked) {
            syncFlag?.receiveOrNull()
        }
    }

    suspend fun release() {
        mutex.withLock(this) {
            isLocked = false
            syncFlag?.close()
            syncFlag = null
        }
    }

    suspend fun lock() {
        if (!isLocked) {
            mutex.withLock(this) {
                syncFlag?.close()
                syncFlag = Channel(Channel.RENDEZVOUS)
                isLocked = true
            }
        }
    }

}
