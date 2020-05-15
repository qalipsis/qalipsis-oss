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
internal class UnicastTopicTest {

    @Test
    internal fun shouldPreventFromUsingClosedTopic() {
        // given
        val topic = UnicastTopic(100, Duration.ofSeconds(1), false)

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
        val topic = UnicastTopic(100, Duration.ofSeconds(1), false)
        runBlocking {
            for (i in 0 until 10) {
                topic.produce(i)
            }
        }

        runBlocking {
            // when
            val subscription = topic.subscribe("any-1")
            // then
            Assertions.assertTrue(subscription.channel.isEmpty)

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
    @ExperimentalCoroutinesApi
    internal fun shouldProvideSubscriptionFromBeginning() {
        // given
        val topic = UnicastTopic(100, Duration.ofSeconds(1), true)
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
        val topic = UnicastTopic(100, Duration.ofSeconds(1), false)
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
        val topic = UnicastTopic(100, Duration.ofMillis(5), false)
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
        val topic = UnicastTopic(100, Duration.ofSeconds(1), false)
        runBlocking {
            for (i in 0 until 10) {
                topic.produce(i)
            }
        }

        runBlocking {
            // when
            val subscription1 = topic.subscribe("any-1")
            val subscription2 = topic.subscribe("any-2")
            // then
            Assertions.assertNotSame(subscription1, subscription2)

            // when
            for (i in 10 until 20) {
                topic.produce(i)
            }
            // then
            for (i in 10 until 20 step 2) {
                Assertions.assertEquals(i, subscription1.pollValue())
                Assertions.assertEquals(i + 1, subscription2.pollValue())
            }
        }
    }

    @Test
    internal fun shouldProvideTwoDifferentSubscriptionsFromBeginning() {
        // given
        val topic = UnicastTopic(100, Duration.ofSeconds(1), true)
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
            for (i in 0 until 20 step 2) {
                Assertions.assertEquals(i, subscription1.pollValue())
                Assertions.assertEquals(i + 1, subscription2.pollValue())
            }
        }
    }

    @Test
    internal fun shouldStillProvideRecordsToOpenSubscriptions() {
        // given
        val topic = UnicastTopic(100, Duration.ofSeconds(1), false)
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
            for (i in 10 until 20) {
                Assertions.assertEquals(i, subscription2.pollValue())
            }
        }
    }
}