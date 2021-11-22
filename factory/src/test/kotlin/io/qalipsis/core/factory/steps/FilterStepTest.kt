package io.qalipsis.core.factory.steps

import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
internal class FilterStepTest {

    @Test
    @Timeout(1)
    fun `should forward input when input matches`() = runBlockingTest {
        val step = FilterStep<Long>("", null) { value -> value > 0 }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 1L)

        step.execute(ctx)
        val output = (ctx.output as Channel).receive()

        Assertions.assertEquals(1L, output)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(1)
    fun `should close output when input does not match`() = runBlockingTest {
        val step = FilterStep<Long>("", null) { value -> value > 0 }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = -1L)

        step.execute(ctx)

        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
