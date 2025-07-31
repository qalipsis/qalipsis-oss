/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.events

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger


@Timeout(30)
internal class AbstractBufferedEventsPublisherTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    fun `should not log when logger level is OFF`() = testCoroutineDispatcher.runTest {
        val publisher =
            TestAbstractBufferedEventsPublisher(this, EventLevel.OFF, Duration.ofMillis(500), batchSize = 1000)
        publisher.publish(Event(EVENT_NAME, EventLevel.TRACE))
        publisher.publish(Event(EVENT_NAME, EventLevel.DEBUG))
        publisher.publish(Event(EVENT_NAME, EventLevel.INFO))
        publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        publisher.publish(Event(EVENT_NAME, EventLevel.ERROR))

        Assertions.assertTrue(publisher.bufferedData.isEmpty())
    }

    @Test
    fun `should log all when logger level is TRACE`() = testCoroutineDispatcher.runTest {
        val publisher =
            TestAbstractBufferedEventsPublisher(this, EventLevel.TRACE, Duration.ofMillis(500), batchSize = 1000)

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
    fun `should log from DEBUG when logger level is DEBUG`() = testCoroutineDispatcher.runTest {
        val publisher =
            TestAbstractBufferedEventsPublisher(this, EventLevel.DEBUG, Duration.ofMillis(500), batchSize = 1000)

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
    fun `should log from INFO when logger level is INFO`() = testCoroutineDispatcher.runTest {
        val publisher =
            TestAbstractBufferedEventsPublisher(this, EventLevel.INFO, Duration.ofMillis(500), batchSize = 1000)

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
    fun `should log from WARN when logger level is WARN`() = testCoroutineDispatcher.runTest {
        val publisher =
            TestAbstractBufferedEventsPublisher(this, EventLevel.WARN, Duration.ofMillis(500), batchSize = 1000)

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
    fun `should log only ERROR when logger level is ERROR`() = testCoroutineDispatcher.runTest {
        val publisher =
            TestAbstractBufferedEventsPublisher(this, EventLevel.ERROR, Duration.ofMillis(500), batchSize = 1000)

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
    fun `should publish when buffer is full`() = testCoroutineDispatcher.run {
        val publisher = TestAbstractBufferedEventsPublisher(this, EventLevel.INFO, Duration.ofMinutes(10), 10, 2)
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
    fun `should publish when linger time is over`() = testCoroutineDispatcher.run {
        val publisher = TestAbstractBufferedEventsPublisher(this, EventLevel.INFO, Duration.ofMillis(400), 100, 2)
        publisher.start()

        repeat(2) {
            delay(300)
            publisher.publish(Event(EVENT_NAME, EventLevel.WARN))
        }

        publisher.publicationCountDownLatch.await()
        assertEquals(2, publisher.executionTimes.size)
        assertEquals(2, publisher.publishedCount.get())
    }

    @Test
    @Timeout(5)
    fun `should log even when logs are faster than publish`() = testCoroutineDispatcher.run {
        val publisher = TestAbstractBufferedEventsPublisher(this, EventLevel.INFO, Duration.ofMinutes(1), 1, 30) {
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
    fun `should support concurrent logs`() = testCoroutineDispatcher.run {
        val publisher = TestAbstractBufferedEventsPublisher(this, EventLevel.INFO, Duration.ofMinutes(1), 1000)
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
    fun `stopping the logger should force publish`() = testCoroutineDispatcher.runTest {
        val publisher = TestAbstractBufferedEventsPublisher(this, EventLevel.INFO, Duration.ofMinutes(1), 100, 1)

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

    class TestAbstractBufferedEventsPublisher(
        coroutineScope: CoroutineScope,
        loggableLevel: EventLevel,
        lingerDuration: Duration,
        batchSize: Int,
        expectedPublications: Int = Int.MAX_VALUE,
        private val doOnPublish: (() -> Unit) = {}
    ) :
        AbstractBufferedEventsPublisher(loggableLevel, lingerDuration, batchSize, coroutineScope) {

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
