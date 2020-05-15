package io.evolue.core.factory.events

import io.evolue.api.events.EventLevel
import io.evolue.core.factory.eventslogger.BufferedEventLogger
import io.evolue.core.factory.eventslogger.elasticsearch.ElasticsearchEventLoggerTest
import io.evolue.test.coroutines.AbstractCoroutinesTest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.time.Instant
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
internal class BufferedEventLoggerTest : AbstractCoroutinesTest() {

    @Test
    internal fun `should not log when logger level is OFF`() {
        val logger = TestBufferedEventLogger(EventLevel.OFF, Duration.ofMillis(500), batchSize = 1000)

        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        Assertions.assertTrue(logger.bufferedData.isEmpty())
    }

    @Test
    internal fun `should log all when logger level is TRACE`() {
        val logger = TestBufferedEventLogger(EventLevel.TRACE, Duration.ofMillis(500), batchSize = 1000)

        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        Assertions.assertEquals(5, logger.bufferedData.size)
        var index = 0
        Assertions.assertEquals(EventLevel.TRACE, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.DEBUG, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.INFO, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.WARN, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.ERROR, logger.bufferedData[index++].level)
    }

    @Test
    internal fun `should log from DEBUG when logger level is DEBUG`() {
        val logger = TestBufferedEventLogger(EventLevel.DEBUG, Duration.ofMillis(500), batchSize = 1000)

        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        Assertions.assertEquals(4, logger.bufferedData.size)
        var index = 0
        Assertions.assertEquals(EventLevel.DEBUG, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.INFO, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.WARN, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.ERROR, logger.bufferedData[index++].level)
    }

    @Test
    internal fun `should log from INFO when logger level is INFO`() {
        val logger = TestBufferedEventLogger(EventLevel.INFO, Duration.ofMillis(500), batchSize = 1000)

        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        Assertions.assertEquals(3, logger.bufferedData.size)
        var index = 0
        Assertions.assertEquals(EventLevel.INFO, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.WARN, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.ERROR, logger.bufferedData[index++].level)
    }

    @Test
    internal fun `should log from WARN when logger level is WARN`() {
        val logger = TestBufferedEventLogger(EventLevel.WARN, Duration.ofMillis(500), batchSize = 1000)

        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        Assertions.assertEquals(2, logger.bufferedData.size)
        var index = 0
        Assertions.assertEquals(EventLevel.WARN, logger.bufferedData[index++].level)
        Assertions.assertEquals(EventLevel.ERROR, logger.bufferedData[index++].level)
    }

    @Test
    internal fun `should log only ERROR when logger level is ERROR`() {
        val logger = TestBufferedEventLogger(EventLevel.ERROR, Duration.ofMillis(500), batchSize = 1000)

        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        Assertions.assertEquals(1, logger.bufferedData.size)
        var index = 0
        Assertions.assertEquals(EventLevel.ERROR, logger.bufferedData[index++].level)
    }

    @Test
    @Timeout(1)
    internal fun `should publish when buffer is full`() {
        val logger = TestBufferedEventLogger(EventLevel.INFO, Duration.ofMinutes(1), 100, 2)
        logger.start()

        repeat(250) {
            logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        }

        logger.publicationCountDownLatch.await()
        Assertions.assertEquals(2, logger.executionTimes.size)
        Assertions.assertEquals(200, logger.publishedCount.get())
    }

    @Test
    @Timeout(1)
    internal fun `should publish when linger time is over`() {
        val logger = TestBufferedEventLogger(EventLevel.INFO, Duration.ofMillis(80), 100, 2)
        logger.start()

        runBlocking {
            repeat(3) {
                delay(60)
                logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
            }
        }

        logger.publicationCountDownLatch.await()
        Assertions.assertEquals(2, logger.executionTimes.size)
        Assertions.assertEquals(2, logger.publishedCount.get())
    }

    @Test
    @Timeout(2)
    internal fun `should log even when logs are faster than publish`() {
        val logger = TestBufferedEventLogger(EventLevel.INFO, Duration.ofMinutes(1), 1, 30) {
            Thread.sleep(30)
        }
        logger.start()

        repeat(30) {
            logger.warn(ElasticsearchEventLoggerTest.EVENT_NAME, ElasticsearchEventLoggerTest.EVENT_VALUE,
                ElasticsearchEventLoggerTest.EVENT_TAGS_MAP)
        }

        logger.publicationCountDownLatch.await()
        Assertions.assertEquals(30, logger.executionTimes.size)
        Assertions.assertEquals(30, logger.publishedCount.get())
    }


    @Test
    @Timeout(1)
    internal fun `should support concurrent logs`() {
        val logger = TestBufferedEventLogger(EventLevel.INFO, Duration.ofMinutes(1), 1000)
        logger.start()

        val job1 = GlobalScope.launch {
            repeat(300) {
                logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
            }
        }
        val job2 = GlobalScope.launch {
            repeat(300) {
                logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
            }
        }
        runBlocking {
            repeat(300) {
                logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
            }
            job1.join()
            job2.join()
        }

        Assertions.assertEquals(900, logger.bufferedData.size)
    }

    @Test
    @Timeout(1)
    internal fun `stopping the logger should force publish`() {
        val logger = TestBufferedEventLogger(EventLevel.INFO, Duration.ofMinutes(1), 100, 1)

        logger.start()
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
        logger.stop()

        logger.publicationCountDownLatch.await()
        Assertions.assertEquals(1, logger.executionTimes.size)
        Assertions.assertEquals(1, logger.publishedCount.get())
    }

    companion object {

        const val EVENT_NAME = "event-name"

        const val EVENT_VALUE = "my-value"

        val EVENT_TAGS_MAP = mapOf("key-1" to "value-1", "key-2" to "value-2")

    }

    class TestBufferedEventLogger(
        loggableLevel: EventLevel,
        lingerDuration: Duration,
        private val batchSize: Int,
        expectedPublications: Int = Int.MAX_VALUE,
        private val doOnPublish: (() -> Unit) = {}) :
        BufferedEventLogger(loggableLevel, lingerDuration, batchSize) {

        val executionTimes = mutableListOf<Instant>()

        val publicationCountDownLatch = CountDownLatch(expectedPublications)

        val bufferedData: LinkedList<Event>
            get() = LinkedList(super.buffer)

        val publishedCount = AtomicInteger()

        override fun publish() {
            doOnPublish()
            cleanBuffer()
            executionTimes.add(Instant.now())
            publicationCountDownLatch.countDown()
        }

        private fun cleanBuffer() {
            var published = 0
            while (!buffer.isEmpty() && published < batchSize) {
                buffer.removeFirst()
                published++
            }
            publishedCount.addAndGet(published)
        }

    }
}