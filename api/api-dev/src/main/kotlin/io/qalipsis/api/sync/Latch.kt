package io.qalipsis.api.sync

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Flag used to suspend the calls to [await] between a [lock] and a [release].
     */
    private var syncFlag: Channel<Unit>? = null

    /**
     * Flag used to suspend the calls to [await] while [releaseAwaiting] is operating.
     */
    private var releaseAwaitingFlag: Channel<Unit>? = null

    private val mutex = Mutex()

    init {
        if (isLocked) {
            syncFlag = Channel(Channel.RENDEZVOUS)
        }
    }


    suspend fun await() {
        if (isLocked) {
            log.trace { "The latch $this is locked, suspending the coroutine." }
            // If a coroutine makes this call while the previously suspended calls are released,
            // it should not be immediately released.
            releaseAwaitingFlag?.receiveCatching()?.getOrNull()
            syncFlag?.receiveCatching()?.getOrNull()
            log.trace { "The latch $this is no longer locked, resuming the coroutine." }
        }
    }

    /**
     * Releases all the awaiting and future calls to [await].
     */
    suspend fun release() {
        if (isLocked) {
            mutex.withLock(this) {
                isLocked = false
                syncFlag?.close()
                syncFlag = null
            }
            log.trace { "Latch $this is now unlocked" }
        }
    }

    /**
     * Suspends all the future calls to [await].
     */
    suspend fun lock() {
        if (!isLocked) {
            mutex.withLock(this) {
                syncFlag?.close()
                syncFlag = Channel(Channel.RENDEZVOUS)
                isLocked = true
            }
            log.trace { "Latch $this is now locked" }
        }
    }

    /**
     * Releases all the awaiting calls to [await] and suspends the concurrent and future ones.
     */
    suspend fun releaseAwaiting() {
        // Forces the concurrent calls to unlock.
        isLocked = true
        releaseAwaitingFlag = Channel(Channel.RENDEZVOUS)

        mutex.withLock(this) {
            // Releases the awaiting ones.
            syncFlag?.close()
            syncFlag = null
            // Suspends the new and futures calls.
            syncFlag = Channel(Channel.RENDEZVOUS)
        }
        // Let the concurrent calls to [await] now wait for [syncFlag] to be released.
        releaseAwaitingFlag?.close()
    }

    /**
     * Forces to unlock all the current and future calls to [await].
     */
    fun cancel() {
        isLocked = false
        syncFlag?.close()
    }

    override fun toString(): String {
        return "Latch(name='${name.takeIf { it.isNotEmpty() } ?: "<No name>"}', isLocked=$isLocked)"
    }

    companion object {

        @JvmStatic
        private val log = KotlinLogging.logger { }

    }
}
