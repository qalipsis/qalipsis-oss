package io.qalipsis.api.messaging

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
internal class BroadcastTopicTest {

    @Test
    internal fun `should prevent from using closed topic`() {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))

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
                topic.produceValue(1)
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
    internal fun `should provide subscription`() {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))
        runBlocking {
            for (i in 0 until 10) {
                topic.produceValue(i)
            }
        }

        runBlocking {
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
    }

    @Test
    internal fun `should cancel subscription`() {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))
        runBlocking {
            // when
            val subscription = topic.subscribe("any-1")
            topic.cancel("any-1")
            // then
            Assertions.assertFalse(subscription.isActive())
        }
    }

    @Test
    internal fun `should cancel idle subscription`() {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofMillis(5))
        runBlocking {
            // when
            val subscription = topic.subscribe("any-1")
            delay(10)
            // then
            Assertions.assertFalse(subscription.isActive())
        }
    }

    @Test
    internal fun `should provide two different subscriptions`() {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))
        runBlocking {
            for (i in 0 until 10) {
                topic.produceValue(i)
            }
        }

        runBlocking {
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
    }

    @Test
    internal fun `should provide records to open subscriptions only`() {
        // given
        val topic = BroadcastTopic<Int>(100, Duration.ofSeconds(1))
        runBlocking {
            for (i in 0 until 10) {
                topic.produceValue(i)
            }
        }

        runBlocking {
            // when
            val subscription1 = topic.subscribe("any-1")
            subscription1.cancel()
            val subscription2 = topic.subscribe("any-2")

            // then
            assertThrows<CancelledSubscriptionException> {
                runBlocking {
                    subscription1.pollValue()
                }
            }
            for (i in 0 until 10) {
                Assertions.assertEquals(i, subscription2.pollValue())
            }
        }
    }

    @Test
    internal fun `should only keep the maximum number of values`() {
        // given
        val topic = BroadcastTopic<Int>(10, Duration.ofSeconds(1))
        runBlocking {
            for (i in 0 until 20) {
                topic.produceValue(i)
            }
        }

        runBlocking {
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
    }

    @Test
    internal fun `should only subscribe from next available value`() {
        // given
        val topic = BroadcastTopic<Int>(0, Duration.ofSeconds(1))
        runBlocking {
            for (i in 0 until 20) {
                topic.produceValue(i)
            }
        }

        runBlocking {
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

}