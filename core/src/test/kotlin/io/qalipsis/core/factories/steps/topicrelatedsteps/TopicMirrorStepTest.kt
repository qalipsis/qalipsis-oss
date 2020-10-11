package io.qalipsis.core.factories.steps.topicrelatedsteps

import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.messaging.Topic
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 *
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
@WithMockk
internal class TopicMirrorStepTest {

    @RelaxedMockK
    lateinit var dataTransferTopic: Topic<String>

    @Test
    @Timeout(3)
    fun `should forward data to channel and topic`() {
        // given
        val step = TopicMirrorStep<String, String>("", dataTransferTopic)
        val ctx = StepTestHelper.createStepContext<String, String>("This is a test")

        // when
        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals("This is a test", output)
        }

        // then
        coVerifyOnce { dataTransferTopic.produceValue(eq("This is a test")) }
        assertFalse(ctx.isExhausted)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    internal fun `should complete the topic`() {
        // given
        val step = TopicMirrorStep<String, String>("", dataTransferTopic)
        val ctx = StepTestHelper.createStepContext<String, String>()
        ctx.isTail = true

        // when
        runBlocking {
            step.execute(ctx)
        }

        // then
        coVerifyOnce { dataTransferTopic.complete() }
        assertFalse(ctx.isExhausted)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}