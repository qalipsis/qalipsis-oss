package io.evolue.core.factories.eventslogger

import io.evolue.api.events.EventLevel
import io.evolue.api.events.EventsLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.core.factories.events.Event
import io.evolue.core.factories.events.toTags
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedDeque

/**
 *
 * @author Eric Jessé
 */
abstract class BufferedEventsLogger(
        private val loggableLevel: EventLevel,
        private val lingerPeriod: Duration = Duration.ofSeconds(10),
        private val batchSize: Int = 2000
) : EventsLogger {

    /**
     * Actual logger to use when a tag supplier is provided.
     */
    private val logMethodWithSupplier =
        if (loggableLevel == EventLevel.OFF) this::noopLogWithSupplier else this::checkLevelAndLogWithSupplier

    private val logMethod = if (loggableLevel == EventLevel.OFF) this::noopLog else this::checkLevelAndLog

    /**
     * List of the buffered events waiting for publication.
     */
    protected val buffer = ConcurrentLinkedDeque<Event>()

    private var running = false

    /**
     * Latch to suspend the publication until the buffer contains enough data.
     */
    private val publicationLatch = SuspendedCountLatch(1)

    @OptIn(ObsoleteCoroutinesApi::class)
    override fun start() {
        if (loggableLevel != EventLevel.OFF) {
            running = true
            val ticker = ticker(lingerPeriod.toMillis(), mode = TickerMode.FIXED_DELAY)
            GlobalScope.launch {
                while (running) {
                    while (buffer.size >= batchSize) {
                        publishSafely()
                    }
                    publicationLatch.reset()
                    // Block until either the count latch is released or the ticker delay is reached.
                    select<Unit> {
                        ticker.onReceive {
                            log.trace("Linger period is reached")
                        }
                        publicationLatch.onRelease {
                            log.trace("Buffer is full: {} items", buffer.size)
                        }
                    }
                    publishSafely()
                }
            }
        }
    }

    override fun stop() {
        if (running) {
            log.info("Stopping the event logger")
            running = false
            runBlocking {
                publicationLatch.release()
            }
            while (buffer.isNotEmpty()) {
                publishSafely()
            }
        } else {
            log.info("The event logger is already stopped")
        }
    }

    override fun log(level: EventLevel, name: String, value: Any?,
                     tagsSupplier: () -> Map<String, String>) {
        logMethodWithSupplier(level, name, value, tagsSupplier)
    }

    override fun log(level: EventLevel, name: String, value: Any?, tags: Map<String, String>) {
        logMethod(level, name, value, tags)
    }

    /**
     * Catch uncaught exceptions thrown from [.publish].
     */
    private fun publishSafely() {
        log.trace("Publishing the data...")
        try {
            publish()
        } catch (e: Throwable) {
            log.warn("Unexpected exception thrown while publishing events for " + this.javaClass.simpleName, e)
        }
    }

    abstract fun publish()

    private fun checkLevelAndLogWithSupplier(level: EventLevel, name: String, value: Any?,
                                             tagsSupplier: () -> Map<String, String>) {
        if (level.ordinal >= loggableLevel.ordinal) {
            checkLevelAndLog(level, name, value, tagsSupplier())
        }
    }

    private fun checkLevelAndLog(level: EventLevel, name: String, value: Any?,
                                 tags: Map<String, String>) {
        if (level.ordinal >= loggableLevel.ordinal) {
            buffer.add(Event(name, level, tags.toTags(), value))
            if (buffer.size >= batchSize && publicationLatch.isSuspended()) {
                // Double-lock check to avoid concurrent releases of the publication latch, but obtain the lock only
                // when it is likely necessary.
                synchronized(this) {
                    if (buffer.size >= batchSize && publicationLatch.isSuspended()) {
                        runBlocking { publicationLatch.release() }
                    }
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @SuppressWarnings("kotlin:S1172")
    private fun noopLogWithSupplier(level: EventLevel, name: String, value: Any?,
                                    tagsSupplier: () -> Map<String, String>) {
        // NO-OP.
    }

    @Suppress("UNUSED_PARAMETER")
    @SuppressWarnings("kotlin:S1172")
    private fun noopLog(level: EventLevel, name: String, value: Any?, tags: Map<String, String>) {
        // NO-OP.
    }

    companion object {
        @JvmStatic
        val log = logger()
    }
}
