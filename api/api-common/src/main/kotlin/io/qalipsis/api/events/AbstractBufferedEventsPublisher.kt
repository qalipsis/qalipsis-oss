package io.qalipsis.api.events

import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.lang.pollFirst
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import kotlin.math.min

/**
 * Aid abstract implementation of [EventsPublisher] that keeps the events in a buffer and publish them periodically
 * based upon the first condition met: the buffer is full or the timeout expired.
 *
 * All the implementations "pushing" events as batches should use this class as parent.
 *
 * @property minLevel minimum level of the events to publish, defaults to TRACE
 * @property lingerPeriod the period of publication, when the buffer was not emptied in the meantime, defaults to 10 seconds
 * @property batchSize the maximal size of the buffer before a publication, defaults to 2000 events
 * @property coroutineScope [CoroutineScope] to start the coroutines
 *
 * @author Eric Jessé
 */
abstract class AbstractBufferedEventsPublisher(
    private val minLevel: EventLevel = EventLevel.TRACE,
    private val lingerPeriod: Duration = Duration.ofSeconds(10),
    private val batchSize: Int = 2000,
    private val coroutineScope: CoroutineScope
) : EventsPublisher {

    protected val buffer = concurrentList<Event>()

    private var ticker: ReceiveChannel<Unit>? = null

    private val mutex = Mutex(false)

    private lateinit var publicationJob: Job

    protected var running = false

    override fun start() = runBlocking {
        running = true
        val latch = Latch(true)
        publicationJob = coroutineScope.launch {
            latch.release()
            while (running) {
                awaitTimeoutAndForward()
            }
        }
        latch.await()
    }

    /**
     * Waits until the timeout is reached and forwards the events from the buffer, in the limit of [batchSize].
     *
     * If the timeout is reached, but the ticker was cancelled once the mutex was acquired, it means that the buffer
     * was just emptied and no more action has to be performed.
     */
    private suspend fun awaitTimeoutAndForward() {
        resetTicker()
        try {
            ticker!!.receiveCatching()
            log.trace { "Timeout is reached" }
            if (buffer.isNotEmpty()) {
                mutex.withLock {
                    if (!ticker!!.isClosedForReceive) {
                        // Only forward the values if the ticker was not yet cancelled.
                        publishSafely(buffer.pollFirst(min(buffer.size, batchSize)))
                    }
                }
            }
        } catch (e: CancellationException) {
            // Called when the ticker is cancelled.
        } catch (e: Exception) {
            log.error(e) { e.message }
        }
    }

    private suspend fun publishSafely(values: List<Event>) {
        log.trace { "Publishing ${values.size} events." }
        tryAndLogOrNull(log) {
            publish(values)
        }
    }

    /**
     * This method can only be called in a context when [timeout] is not null.
     */
    private fun resetTicker() {
        ticker?.cancel()
        ticker =
            ticker(lingerPeriod.toMillis(), context = coroutineScope.coroutineContext, mode = TickerMode.FIXED_DELAY)
    }

    override fun publish(event: Event) {
        if (minLevel != EventLevel.OFF && event.level >= minLevel) {
            buffer.add(event)
            if (buffer.size >= batchSize && running) {
                log.trace { "The buffer is full, starting a coroutine to publish the events" }
                coroutineScope.launch {
                    mutex.withLock {
                        // Verifies the size once again, in case the batch was emptied in the meantime.
                        if (buffer.size >= batchSize) {
                            publishSafely(buffer.pollFirst(min(buffer.size, batchSize)))
                            // Cancels the ticker in order to prevent it from forwarding the data if it reaches
                            // its timeout concurrently.
                            ticker?.cancel()
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        running = false
        ticker?.cancel()
        runBlocking {
            mutex.withLock {
                while (buffer.isNotEmpty()) {
                    publishSafely(buffer.pollFirst(batchSize.coerceAtMost(buffer.size)))
                }
            }
        }
    }

    abstract suspend fun publish(values: List<Event>)

    companion object {

        @JvmStatic
        val log = logger()
    }
}
