package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.lang.pollFirst
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.Slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.LinkedList
import kotlin.math.min


internal class CollectionStep<I>(
    id: StepId,
    private val timeout: Duration?,
    private val batchSize: Int,
    private val coroutineScope: CoroutineScope = GlobalScope
) : AbstractStep<I, List<I>>(id, null) {

    private val buffer = LinkedList<I>()

    private var ticker: ReceiveChannel<Unit>? = null

    private val mutex = Mutex(false)

    private lateinit var publicationJob: Job

    private var running = false

    private val latestContextSlot = Slot<StepContext<I, List<I>>>()

    override suspend fun start(context: StepStartStopContext) {
        super.start(context)
        running = true

        // If there is a timeout, a background coroutine with a ticker forwards the values periodically.
        timeout?.let {
            val latch = Latch(true)
            log.debug("Starting the background coroutine to forward data on timeout")
            publicationJob = coroutineScope.launch {
                latch.release()
                while (running) {
                    awaitTimeoutAndForward()
                }
            }
            latch.await()
            log.debug("Background coroutine to forward data on timeout was started")
        }
    }

    /**
     * Waits until the timeout is reached and forwards the values from the buffer, in the limit of [batchSize].
     *
     * If the timeout is reached, but the ticker was cancelled once the mutex was acquired, it means that the buffer
     * was just emptied and no more action has to be performed.
     */
    private suspend fun awaitTimeoutAndForward() {
        resetTicker()
        try {
            val receiveOrClosed = ticker!!.receiveOrNull()
            if (receiveOrClosed != null && running && buffer.isNotEmpty()) {
                log.trace("Timeout is reached")
                mutex.withLock {
                    // Only forward the values if the ticker was not yet cancelled.
                    if (!ticker!!.isClosedForReceive) {
                        log.trace("Timeout is reached.")
                        val values = buffer.pollFirst(min(buffer.size, batchSize))
                        val context = latestContextSlot.getOrNull()
                        log.trace(
                            "Forwarding ${values.size} records in a batch after timeout with context of minion ${context?.minionId}.")
                        forwardValues(context, values)
                    }
                }
            }
        } catch (e: CancellationException) {
            // Called when the ticker is cancelled.
        } catch (e: Exception) {
            log.error(e.message, e)
        }
    }

    /**
     * This method can only be called in a context when [timeout] is not null.
     */
    private fun resetTicker() {
        ticker?.cancel()
        ticker = ticker(timeout!!.toMillis(), context = coroutineScope.coroutineContext, mode = TickerMode.FIXED_DELAY)
    }

    override suspend fun execute(context: StepContext<I, List<I>>) {
        require(running) { "The step is in termination mode" }
        val input = context.input.receive()

        mutex.withLock {
            buffer.add(input)
            if (buffer.size == batchSize) {
                log.trace("The buffer is full")
                log.trace("Forwarding ${buffer.size} records in a batch with context of minion ${context.minionId}.")
                // We have to perform the forwarding in the same lock scope.
                // Releasing the lock and acquiring it again in a coroutine would place the acquisition at the end of
                // the queue, because the lock is fair. However, we want this operation to have the priority.
                forwardValues(context, buffer.toList())
                buffer.clear()
                log.trace("Values were sent, resetting the ticker.")
                // Cancels the ticker in order to prevent it from forwarding the data if it reaches
                // its timeout concurrently.
                ticker?.cancel()
            } else if (timeout != null) {
                // Releases the potential last step context to let it finish its execution.
                latestContextSlot.getOrNull()?.also {
                    log.trace("Releasing the context of minion ${it.minionId}.")
                    it.release()
                }
                context.lock()
                // Saves the context in case it is required for a release by timeout or close.
                latestContextSlot.set(context)
            }
            return@withLock
        }

        // Waits until the current step context is released by either a future call to [execute] or a
        // a past one to [forwardValues].
        context.await()
    }

    private suspend fun forwardValues(context: StepContext<I, List<I>>?, values: List<I>) {
        context?.apply {
            // Sends all the values into the output of the latest context.
            this.output.send(values)
            // Releases the context in order to let its execution finish and the values be consumed by the next steps.
            this.release()
        }
    }

    override suspend fun stop(context: StepStartStopContext) {
        running = false
        log.trace("Releasing latest step context.")
        latestContextSlot.getOrNull()?.release()
        timeout?.let {
            // The [publicationJob] and the [ticker] only exist if there is a timeout defined.
            log.trace("Cancelling background job.")
            ticker?.cancel()
            publicationJob.cancelAndJoin()
            log.trace("Background job cancelled.")
        }
        buffer.clear()
        super.stop(context)
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }

}
