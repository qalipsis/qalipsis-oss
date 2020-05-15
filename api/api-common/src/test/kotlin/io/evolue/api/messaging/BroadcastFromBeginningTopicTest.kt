package io.evolue.api.messaging

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class BroadcastFromBeginningTopicTest {

    @Test
    internal fun shouldPreventFromUsingClosedTopic() {
        // given
        val topic = BroadcastFromBeginningTopic(100, Duration.ofSeconds(1))

        // when
        topic.close()

        // then
        assertThrows<ClosedTopicException> {
            runBlocking {
                topic.subscribe("any-1")
            }
        }
        assertThrows<ClosedTopicException> {
            runBlocking {
                topic.produce(1)
            }
        }
        assertThrows<ClosedTopicException> {
            runBlocking {
                topic.poll("any-1")
            }
        }
    }

    @Test
    @ExperimentalCoroutinesApi
    internal fun shouldProvideSubscription() {
        // given
        val topic = BroadcastFromBeginningTopic(100, Duration.ofSeconds(1))
        runBlocking {
            for (i in 0 until 10) {
                topic.produce(i)
            }
        }

        runBlocking {
            // when
            val subscription = topic.subscribe("any-1")
            // then
            Assertions.assertFalse(subscription.channel.isEmpty)
            for (i in 0 until 10) {
                Assertions.assertEquals(i, subscription.pollValue())
            }

            // when
            val sameSubscription = topic.subscribe("any-1")
            // then
            Assertions.assertSame(subscription, sameSubscription)

            // when
            for (i in 10 until 20) {
                topic.produce(i)
            }
            // then
            for (i in 10 until 20) {
                Assertions.assertEquals(i, subscription.pollValue())
            }
        }
    }

    @Test
    internal fun shouldCancelSubscription() {
        // given
        val topic = BroadcastFromBeginningTopic(100, Duration.ofSeconds(1))
        runBlocking {
            // when
            val subscription = topic.subscribe("any-1")
            topic.cancel("any-1")
            // then
            Assertions.assertFalse(subscription.isActive())
        }
    }

    @Test
    internal fun shouldCancelIdleSubscription() {
        // given
        val topic = BroadcastFromBeginningTopic(100, Duration.ofMillis(5))
        runBlocking {
            // when
            val subscription = topic.subscribe("any-1")
            delay(10)
            // then
            Assertions.assertFalse(subscription.isActive())
        }
    }

    @Test
    internal fun shouldProvideTwoDifferentSubscriptions() {
        // given
        val topic = BroadcastFromBeginningTopic(100, Duration.ofSeconds(1))
        runBlocking {
            for (i in 0 until 10) {
                topic.produce(i)
            }
        }

        runBlocking {
            // when
            val subscription1 = topic.subscribe("any-1")
            val subscription2 = topic.subscribe("any-2")
            for (i in 10 until 20) {
                topic.produce(i)
            }

            // then
            Assertions.assertNotSame(subscription1, subscription2)
            for (i in 0 until 20) {
                Assertions.assertEquals(i, subscription1.pollValue())
                Assertions.assertEquals(i, subscription2.pollValue())
            }
        }
    }

    @Test
    internal fun shouldStillProvideRecordsToOpenSubscriptions() {
        // given
        val topic = BroadcastFromBeginningTopic(100, Duration.ofSeconds(1))
        runBlocking {
            for (i in 0 until 10) {
                topic.produce(i)
            }
        }

        runBlocking {
            // when
            val subscription1 = topic.subscribe("any-1")
            val subscription2 = topic.subscribe("any-2")
            subscription1.cancel()
            for (i in 10 until 20) {
                topic.produce(i)
            }
            // then
            assertThrows<CancelledSubscriptionException> {
                runBlocking {
                    subscription1.pollValue()
                }
            }
            for (i in 0 until 20) {
                Assertions.assertEquals(i, subscription2.pollValue())
            }
        }
    }
}