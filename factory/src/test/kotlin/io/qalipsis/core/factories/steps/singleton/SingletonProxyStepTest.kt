package io.qalipsis.core.factories.steps.singleton

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.mockk
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.subscriptions.TopicSubscription
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
internal class SingletonProxyStepTest {

    @Test
    @Timeout(3)
    internal fun `should use record from topic`() = runBlockingTest {
        val subscription = mockk<TopicSubscription<Long>> {
            coEvery { pollValue() } returns 123L
        }
        val ctx = StepTestHelper.createStepContext<Long, Long>()
        val topic = mockk<Topic<Long>>(relaxed = true) {
            coEvery { subscribe("${ctx.minionId}-${ctx.stepId}") } returns subscription
        }
        val step = SingletonProxyStep("", topic)

        // when
        step.execute(ctx)

        // then
        Assertions.assertEquals(123L, (ctx.output as Channel).receive())
        coVerifyOrder {
            topic.subscribe("${ctx.minionId}-${ctx.stepId}")
            subscription.pollValue()
        }
        confirmVerified(topic, subscription)
    }
}
