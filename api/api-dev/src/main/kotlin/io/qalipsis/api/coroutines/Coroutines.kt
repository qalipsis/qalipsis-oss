package io.qalipsis.api.coroutines

import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Launches a new coroutine like the standard [launch] does, but additionally increment [jobsLatch] when scheduling
 * the coroutine and decrement when the coroutine is over or the launch failed.
 */
fun CoroutineScope.launch(jobsLatch: SuspendedCountLatch?, block: suspend CoroutineScope.() -> Unit): Job {
    jobsLatch?.blockingIncrement()
    val decremented = AtomicBoolean(false)
    val decrementingMutex = Mutex()
    return try {
        launch {
            try {
                block()
            } finally {
                // This block ensures that the counter is decremented only once in case of concurrent failures
                // in the launching and execution phase.
                decrementingMutex.withLock {
                    if (!decremented.get()) {
                        jobsLatch?.decrement()
                        decremented.set(true)
                    }
                }
            }
        }
    } catch (e: Exception) {
        // This block ensures that the counter is decremented only once in case of concurrent failures
        // in the launching and execution phase.
        runBlocking {
            decrementingMutex.withLock {
                if (!decremented.get()) {
                    jobsLatch?.decrement()
                    decremented.set(true)
                }
            }
        }
        throw e
    }
}
