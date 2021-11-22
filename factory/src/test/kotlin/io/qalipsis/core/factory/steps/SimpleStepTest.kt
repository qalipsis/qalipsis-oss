package io.qalipsis.core.factory.steps

import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class SimpleStepTest {

    @Test
    @Timeout(1)
    fun `should just execute the closure`() = runBlockingTest {
        val step =
            SimpleStep<Long, Long>("", null) { context -> context.send(context.receive()) }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 123L)

        step.execute(ctx)
        val output = (ctx.output as Channel).receive()
        assertEquals(123L, output)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        Assertions.assertFalse(ctx.isExhausted)
    }

    @Test
    @Timeout(1)
    fun `should throw the exception`() {
        val step = SimpleStep<Long, Long>("", null) { throw RuntimeException("This is an error") }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = -1L)

        Assertions.assertThrows(RuntimeException::class.java) {
            runBlockingTest {
                step.execute(ctx)
            }
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        Assertions.assertFalse(ctx.isExhausted)
    }
}
