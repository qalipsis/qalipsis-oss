package io.qalipsis.api.events

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.time.Instant
import java.util.LinkedList

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger


internal class AbstractBufferedEventsPublisherTest {

    @Test
    fun `should not log when logger level is OFF`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.OFF, Duration.ofMillis(500), batchSize = 1000)
        publisher.publish(Event(EVENT_NAME, EventLevel.TRACE))
        publisher.publish(Event(EVENT_NAME, EventLevel.DEBUG))
        publisher.publish(Event(EVENT_NAME, EventLevel.INFO))
        publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        publisher.publish(Event(EVENT_NAME, EventLevel.ERROR))

        Assertions.assertTrue(publisher.bufferedData.isEmpty())
    }

    @Test
    fun `should log all when logger level is TRACE`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.TRACE, Duration.ofMillis(500), batchSize = 1000)

        publisher.publish(Event(EVENT_NAME, EventLevel.TRACE))
        publisher.publish(Event(EVENT_NAME, EventLevel.DEBUG))
        publisher.publish(Event(EVENT_NAME, EventLevel.INFO))
        publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        publisher.publish(Event(EVENT_NAME, EventLevel.ERROR))

        assertThat(publisher.bufferedData).all {
            hasSize(5)
            index(0).prop(Event::level).isEqualTo(EventLevel.TRACE)
            index(1).prop(Event::level).isEqualTo(EventLevel.DEBUG)
            index(2).prop(Event::level).isEqualTo(EventLevel.INFO)
            index(3).prop(Event::level).isEqualTo(EventLevel.WARN)
            index(4).prop(Event::level).isEqualTo(EventLevel.ERROR)
        }
    }

    @Test
    fun `should log from DEBUG when logger level is DEBUG`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.DEBUG, Duration.ofMillis(500), batchSize = 1000)

        publisher.publish(Event(EVENT_NAME, EventLevel.TRACE))
        publisher.publish(Event(EVENT_NAME, EventLevel.DEBUG))
        publisher.publish(Event(EVENT_NAME, EventLevel.INFO))
        publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        publisher.publish(Event(EVENT_NAME, EventLevel.ERROR))

        assertThat(publisher.bufferedData).all {
            hasSize(4)
            index(0).prop(Event::level).isEqualTo(EventLevel.DEBUG)
            index(1).prop(Event::level).isEqualTo(EventLevel.INFO)
            index(2).prop(Event::level).isEqualTo(EventLevel.WARN)
            index(3).prop(Event::level).isEqualTo(EventLevel.ERROR)
        }
    }

    @Test
    fun `should log from INFO when logger level is INFO`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.INFO, Duration.ofMillis(500), batchSize = 1000)

        publisher.publish(Event(EVENT_NAME, EventLevel.TRACE))
        publisher.publish(Event(EVENT_NAME, EventLevel.DEBUG))
        publisher.publish(Event(EVENT_NAME, EventLevel.INFO))
        publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        publisher.publish(Event(EVENT_NAME, EventLevel.ERROR))

        assertThat(publisher.bufferedData).all {
            hasSize(3)
            index(0).prop(Event::level).isEqualTo(EventLevel.INFO)
            index(1).prop(Event::level).isEqualTo(EventLevel.WARN)
            index(2).prop(Event::level).isEqualTo(EventLevel.ERROR)
        }
    }

    @Test
    fun `should log from WARN when logger level is WARN`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.WARN, Duration.ofMillis(500), batchSize = 1000)

        publisher.publish(Event(EVENT_NAME, EventLevel.TRACE))
        publisher.publish(Event(EVENT_NAME, EventLevel.DEBUG))
        publisher.publish(Event(EVENT_NAME, EventLevel.INFO))
        publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        publisher.publish(Event(EVENT_NAME, EventLevel.ERROR))

        assertThat(publisher.bufferedData).all {
            hasSize(2)
            index(0).prop(Event::level).isEqualTo(EventLevel.WARN)
            index(1).prop(Event::level).isEqualTo(EventLevel.ERROR)
        }
    }

    @Test
    fun `should log only ERROR when logger level is ERROR`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.ERROR, Duration.ofMillis(500), batchSize = 1000)

        publisher.publish(Event(EVENT_NAME, EventLevel.TRACE))
        publisher.publish(Event(EVENT_NAME, EventLevel.DEBUG))
        publisher.publish(Event(EVENT_NAME, EventLevel.INFO))
        publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        publisher.publish(Event(EVENT_NAME, EventLevel.ERROR))

        assertThat(publisher.bufferedData).all {
            hasSize(1)
            index(0).prop(Event::level).isEqualTo(EventLevel.ERROR)
        }
    }

    @Test
    @Timeout(5)
    fun `should publish when buffer is full`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.INFO, Duration.ofMinutes(10), 10, 2)
        publisher.start()

        repeat(25) {
            publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        }

        publisher.publicationCountDownLatch.await()
        assertEquals(2, publisher.executionTimes.size)
        assertEquals(20, publisher.publishedCount.get())
    }

    @Test
    @Timeout(5)
    fun `should publish when linger time is over`() = runBlocking {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.INFO, Duration.ofMillis(400), 100, 2)
        publisher.start()

        repeat(3) {
            delay(300)
            publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        }

        publisher.publicationCountDownLatch.await()
        assertEquals(2, publisher.executionTimes.size)
        assertEquals(2, publisher.publishedCount.get())
    }

    @Test
    @Timeout(5)
    fun `should log even when logs are faster than publish`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.INFO, Duration.ofMinutes(1), 1, 30) {
            Thread.sleep(30)
        }
        publisher.start()

        repeat(30) {
            publisher.publish(Event(EVENT_NAME, EventLevel.ERROR))
        }

        publisher.publicationCountDownLatch.await()
        assertEquals(30, publisher.executionTimes.size)
        assertEquals(30, publisher.publishedCount.get())
    }


    @Test
    @Timeout(1)
    fun `should support concurrent logs`() = runBlockingTest {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.INFO, Duration.ofMinutes(1), 1000)
        publisher.start()
        val job1 = launch {
            repeat(300) {
                publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
            }
        }
        val job2 = launch {
            repeat(300) {
                publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
            }
        }

        repeat(300) {
            publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        }
        job1.join()
        job2.join()

        assertEquals(900, publisher.bufferedData.size)
    }

    @Test
    @Timeout(5)
    fun `stopping the logger should force publish`() {
        val publisher = TestAbstractBufferedEventsPublisher(EventLevel.INFO, Duration.ofMinutes(1), 100, 1)

        publisher.start()
        publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        publisher.stop()

        publisher.publicationCountDownLatch.await()
        assertEquals(1, publisher.executionTimes.size)
        assertEquals(1, publisher.publishedCount.get())
    }

    companion object {

        const val EVENT_NAME = "event-name"

    }

    @ObsoleteCoroutinesApi

    class TestAbstractBufferedEventsPublisher(
        loggableLevel: EventLevel,
        lingerDuration: Duration,
        batchSize: Int,
        expectedPublications: Int = Int.MAX_VALUE,
        private val doOnPublish: (() -> Unit) = {}) :
        AbstractBufferedEventsPublisher(loggableLevel, lingerDuration, batchSize) {

        val executionTimes = mutableListOf<Instant>()

        val publicationCountDownLatch = CountDownLatch(expectedPublications)

        val bufferedData: LinkedList<Event>
            get() = LinkedList(buffer)

        val publishedCount = AtomicInteger()

        override suspend fun publish(values: List<Event>) {
            doOnPublish()
            publishedCount.addAndGet(values.size)
            executionTimes.add(Instant.now())
            publicationCountDownLatch.countDown()
        }

    }
}
