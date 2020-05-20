package io.evolue.core.factory.steps.singleton

import io.evolue.api.messaging.ClosedTopicException
import io.evolue.api.messaging.Topic
import io.evolue.api.messaging.TopicMode
import io.evolue.api.messaging.topic
import io.evolue.core.exceptions.NotInitializedStepException
import io.evolue.core.factory.steps.StepTestHelper
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows

/**
 * @author Eric Jessé
 */
internal class SingletonProxyStepTest {

    @Test
    @Timeout(1)
    internal fun shouldNotExecuteWhenNotInitialized() {
        val remoteChannel = Channel<Long>(100)
        val topic = topic(TopicMode.UNICAST, fromBeginning = true)
        val step = SingletonProxyStep("", remoteChannel, topic)
        val ctx = StepTestHelper.createStepContext<Long, Long>()

        // then
        assertThrows<NotInitializedStepException> {
            runBlocking {
                step.execute(ctx)
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun initShouldStartBroadcastChannelConsumptionWithoutFilter() {
        val remoteChannel = Channel<Int>(100)
        val topic = topic(TopicMode.UNICAST, fromBeginning = true)
        val step = SingletonProxyStep("", remoteChannel, topic)

        // when
        runBlocking {
            for (i in 0 until 10) {
                remoteChannel.send(i)
            }
            step.init()
            for (i in 10 until 20) {
                remoteChannel.send(i)
            }
        }

        // then
        runBlocking {
            for (i in 0 until 20) {
                Assertions.assertEquals(i, topic.subscribe("").pollValue())
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun initShouldStartBroadcastChannelConsumptionWithFilter() {
        val remoteChannel = Channel<Int>(100)
        val topic = topic(TopicMode.UNICAST, fromBeginning = true)
        val step = SingletonProxyStep("", remoteChannel, topic) { value -> value >= 5 }

        // when
        runBlocking {
            for (i in 0 until 10) {
                remoteChannel.send(i)
            }
            step.init()
            for (i in 10 until 20) {
                remoteChannel.send(i)
            }
        }

        // then
        runBlocking {
            for (i in 5 until 20) {
                Assertions.assertEquals(i, topic.subscribe("").pollValue())
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun closeShouldStopChannelConsumption() {
        val remoteChannel = Channel<Topic.Record>()
        val topic = topic(TopicMode.UNICAST)
        val step = SingletonProxyStep("", remoteChannel, topic)

        // when
        runBlocking {
            step.destroy()
        }

        // then
        assertThrows<ClosedTopicException> {
            runBlocking {
                topic.produce(1)
            }
        }
        Assertions.assertTrue(remoteChannel.isClosedForReceive)
        Assertions.assertTrue(remoteChannel.isClosedForSend)
    }

    @Test
    @Timeout(3)
    internal fun shouldUseRecordFromTopic() {
        val remoteChannel = mockk<Channel<Long>>(relaxed = true)
        val subscription = mockk<Topic.TopicSubscription>(relaxed = true) {
            coEvery { pollValue() } returns "my-value"
        }
        val ctx = StepTestHelper.createStepContext<Long, Long>()
        val topic = mockk<Topic>(relaxed = true) {
            coEvery { subscribe(ctx.minionId) } returns subscription
        }
        val step = SingletonProxyStep("", remoteChannel, topic)
        runBlocking {
            step.init()
        }

        // when
        runBlocking {
            step.execute(ctx)
        }

        // then
        runBlocking {
            Assertions.assertEquals("my-value", (ctx.output as Channel).receive())
            coVerifyOrder {
                topic.subscribe(ctx.minionId)
                subscription.pollValue()
            }
            confirmVerified(topic, subscription)
        }
    }
}