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

package io.qalipsis.api.messaging

import assertk.assertThat
import assertk.assertions.hasSize
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class BroadcastTopicTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    fun `should prevent from using closed topic`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))

        // when
        topic.close()

        // then
        assertThrows<ClosedTopicException> {
            topic.subscribe("any-1")
        }
        assertThrows<ClosedTopicException> {
            topic.produceValue(1)
        }
        assertThrows<ClosedTopicException> {
            topic.poll("any-1")
        }
    }

    @Test
    fun `should provide subscription`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))
        for (i in 0 until 10) {
            topic.produceValue(i)
        }

        // when
        val subscription = topic.subscribe("any-1")
        // then
        for (i in 0 until 10) {
            Assertions.assertEquals(i, subscription.pollValue())
        }

        // when
        val sameSubscription = topic.subscribe("any-1")
        // then
        Assertions.assertSame(subscription, sameSubscription)

        // when
        for (i in 10 until 20) {
            topic.produceValue(i)
        }
        // then
        for (i in 10 until 20) {
            Assertions.assertEquals(i, subscription.pollValue())
        }
    }

    @Test
    @Timeout(10)
    fun `should consume subscription`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))
        for (i in 0 until 10) {
            topic.produceValue(i)
        }
        val counter = SuspendedCountLatch(10)
        val received = concurrentSet<Int>()

        // when
        val subscription = topic.subscribe("any-1")
        subscription.onReceiveValue {
            received += it
            counter.decrement()
        }
        counter.await()

        // then
        assertThat(received).hasSize(10)

        subscription.cancel()
    }

    @Test
    fun `should cancel subscription`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))

        // when
        val subscription = topic.subscribe("any-1")
        topic.cancel("any-1")

        // then
        Assertions.assertFalse(subscription.isActive())
    }

    @Test
    fun `should cancel idle subscription`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofMillis(20))

        // when
        val subscription = topic.subscribe("any-1")
        delay(100)

        // then
        Assertions.assertFalse(subscription.isActive())
    }

    @Test
    fun `should provide two different subscriptions`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))
        for (i in 0 until 10) {
            topic.produceValue(i)
        }

        // when
        val subscription1 = topic.subscribe("any-1")
        val subscription2 = topic.subscribe("any-2")
        for (i in 10 until 20) {
            topic.produceValue(i)
        }

        // then
        Assertions.assertNotSame(subscription1, subscription2)
        for (i in 0 until 20) {
            Assertions.assertEquals(i, subscription1.pollValue())
            Assertions.assertEquals(i, subscription2.pollValue())
        }
    }

    @Test
    fun `should provide records to open subscriptions only`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))
        for (i in 0 until 10) {
            topic.produceValue(i)
        }

        // when
        val subscription1 = topic.subscribe("any-1")
        subscription1.cancel()
        val subscription2 = topic.subscribe("any-2")

        // then
        assertThrows<CancelledSubscriptionException> {
            subscription1.pollValue()
        }
        for (i in 0 until 10) {
            Assertions.assertEquals(i, subscription2.pollValue())
        }
    }

    @Test
    fun `should only keep the maximum number of values`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(10, Duration.ofSeconds(1))
        for (i in 0 until 20) {
            topic.produceValue(i)
        }

        // when
        val subscription = topic.subscribe("any-1")

        for (i in 20 until 30) {
            topic.produceValue(i)
        }

        // then
        for (i in 10 until 30) {
            Assertions.assertEquals(i, subscription.pollValue())
        }
    }

    @Test
    fun `should only subscribe from next available value`() = testCoroutineDispatcher.run {
        // given
        val topic = BroadcastTopic<Int>(0, Duration.ofSeconds(1))
        for (i in 0 until 20) {
            topic.produceValue(i)
        }

        // when
        val subscription = topic.subscribe("any-1")

        for (i in 20 until 30) {
            topic.produceValue(i)
        }

        // then
        for (i in 20 until 30) {
            Assertions.assertEquals(i, subscription.pollValue())
        }
    }
}
