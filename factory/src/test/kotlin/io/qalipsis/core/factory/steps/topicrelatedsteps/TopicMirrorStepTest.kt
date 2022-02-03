package io.qalipsis.core.factory.steps.topicrelatedsteps

import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.messaging.Topic
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 *
 * @author Eric Jessé
 */
@WithMockk
internal class TopicMirrorStepTest {

    @RelaxedMockK
    lateinit var dataTransferTopic: Topic<String>

    @Test
    @Timeout(3)
    fun `should forward data to channel and topic`() = runBlockingTest {
        // given
        val step = TopicMirrorStep<String, String>("", dataTransferTopic)
        val ctx = StepTestHelper.createStepContext<String, String>("This is a test").also { it.isTail = false }

        // when
        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals("This is a test", output)

        // then
        coVerifyOnce { dataTransferTopic.produceValue(eq("This is a test")) }
        assertFalse(ctx.isExhausted)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    internal fun `should complete the topic`() = runBlockingTest {
        // given
        val step = TopicMirrorStep<String, String>("", dataTransferTopic)
        val ctx = DefaultCompletionContext(
            campaignId = "my-campaign",
            scenarioId = "my-scenario",
            minionId = "my-minion",
            lastExecutedStepId = "step-1",
            errors = emptyList()
        )

        // when
        step.complete(ctx)

        // then
        coVerifyOnce { dataTransferTopic.complete() }
    }
}
