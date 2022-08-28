/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.messaging

import assertk.assertThat
import assertk.assertions.hasSize
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.channels.Channel
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
internal class UnicastTopicTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(10)
    internal fun `should prevent from using closed topic`(): Unit = testCoroutineDispatcher.run {
        // given
        val topic = UnicastTopic<Int>(100, Duration.ofSeconds(1))

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
    @Timeout(10)
    internal fun `should provide subscription`() = testCoroutineDispatcher.run {
        // given
        val topic = UnicastTopic<Int>(100, Duration.ofSeconds(1))
        for (i in 0 until 10) {
            topic.produceValue(i)
        }

        // when
        val subscription = topic.subscribe("any-1")
        val sameSubscription = topic.subscribe("any-1")
        // then
        Assertions.assertSame(subscription, sameSubscription)

        // when
        for (i in 10 until 20) {
            topic.produceValue(i)
        }

        // then
        for (i in 0 until 20) {
            Assertions.assertEquals(i, subscription.pollValue())
        }
    }

    @Test
    @Timeout(10)
    fun `should consume subscription`() = testCoroutineDispatcher.run {
        // given
        val topic = UnicastTopic<Int>(100, Duration.ofSeconds(1))
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
    }

    @Test
    @Timeout(10)
    internal fun `should provide subscription from beginning`() = testCoroutineDispatcher.run {
        // given
        val topic = UnicastTopic<Int>(100, Duration.ofSeconds(10))
        for (i in 0 until 10) {
            topic.produceValue(i)
        }

        // when
        val subscription = topic.subscribe("any-1")
        // then
        Assertions.assertFalse((subscription.getProperty("channel") as Channel<Int>).isEmpty)
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
    internal fun `should cancel subscription`() = testCoroutineDispatcher.run {
        // given
        val topic = UnicastTopic<Int>(100, Duration.ofSeconds(1))

        // when
        val subscription = topic.subscribe("any-1")
        topic.cancel("any-1")

        // then
        Assertions.assertFalse(subscription.isActive())
    }

    @Test
    @Timeout(10)
    internal fun `should cancel idle subscription`() = testCoroutineDispatcher.run {
        // given
        val topic = UnicastTopic<Int>(100, Duration.ofMillis(20))

        // when
        val subscription = topic.subscribe("any-1")
        delay(100)

        // then
        Assertions.assertFalse(subscription.isActive())
    }

    @Test
    @Timeout(10)
    internal fun `should provide two different subscriptions from beginning`() = testCoroutineDispatcher.run {
        // given
        val topic = UnicastTopic<Int>(100, Duration.ofSeconds(1))

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
        for (i in 0 until 20 step 2) {
            Assertions.assertEquals(i, subscription1.pollValue())
            Assertions.assertEquals(i + 1, subscription2.pollValue())
        }
    }

    @Test
    @Timeout(10)
    internal fun `should provide records to open subscriptions only`() = testCoroutineDispatcher.run {
        // given
        val topic = UnicastTopic<Int>(100, Duration.ofSeconds(1))
        for (i in 0 until 10) {
            topic.produceValue(i)

        }

        // when
        val subscription1 = topic.subscribe("any-1")
        val subscription2 = topic.subscribe("any-2")
        subscription1.cancel()
        for (i in 10 until 20) {
            topic.produceValue(i)
        }

        // then
        assertThrows<CancelledSubscriptionException> {
            subscription1.pollValue()
        }
        for (i in 0 until 20) {
            Assertions.assertEquals(i, subscription2.pollValue())
        }
    }
}
