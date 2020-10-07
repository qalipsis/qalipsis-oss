package io.qalipsis.core.factories.steps

import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
internal class FilterStepTest {

    @Test
    @Timeout(1)
    fun shouldForwardInputWhenInputMatches() {
        val step = FilterStep<Long>("", null) { value -> value > 0 }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 1L)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals(1L, output)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(1)
    fun shouldCloseOutputWhenInputDoesNotMatch() {
        val step = FilterStep<Long>("", null) { value -> value > 0 }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = -1L)

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
