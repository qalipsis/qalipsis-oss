package io.qalipsis.api.messaging

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class LoopTopicTest {


    @Test
    internal fun `should prevent from using closed topic`() = runBlockingTest {
        // given
        val topic = LoopTopic<Int>(Duration.ofSeconds(1))

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

    internal fun `should provide subscription`() = runBlockingTest {
        // given
        val topic = LoopTopic<Int>(Duration.ofSeconds(1))
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
    internal fun `should cancel subscription`() = runBlockingTest {
        // given
        val topic = LoopTopic<Int>(Duration.ofSeconds(1))

        // when
        val subscription = topic.subscribe("any-1")
        topic.cancel("any-1")

        // then
        Assertions.assertFalse(subscription.isActive())
    }

    @Test
    internal fun `should cancel idle subscription`() = runBlocking {
        // given
        val topic = LoopTopic<Int>(Duration.ofMillis(5))

        // when
        val subscription = topic.subscribe("any-1")
        delay(10)

        // then
        Assertions.assertFalse(subscription.isActive())
    }

    @Test
    internal fun `should provide two different subscriptions`() = runBlockingTest {
        // given
        val topic = LoopTopic<Int>(Duration.ofSeconds(1))
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
    internal fun `should provide records to open subscriptions only`() = runBlockingTest {
        // given
        val topic = LoopTopic<Int>(Duration.ofSeconds(1))
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
    internal fun `should provide infinite data once completed`() = runBlockingTest {
        // given
        val topic = LoopTopic<Int>(Duration.ofSeconds(1))
        for (i in 0 until 20) {
            topic.produceValue(i)
        }
        topic.complete()

        // when
        val subscription = topic.subscribe("any-1")

        // then
        repeat(10) {
            for (i in 0 until 20) {
                Assertions.assertEquals(i, subscription.pollValue())
            }
        }
    }
}
