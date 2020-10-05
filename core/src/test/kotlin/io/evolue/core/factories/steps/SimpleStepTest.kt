package io.evolue.core.factories.steps

import io.evolue.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Suppress("EXPERIMENTAL_API_USAGE")
internal class SimpleStepTest {

    @Test
    @Timeout(1)
    fun shouldJustExecuteTheClosure() {
        val step =
            SimpleStep<Long, Long>("", null) { context -> (context.output as Channel).send(context.input.receive()) }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 123L)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(123L, output)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        Assertions.assertFalse(ctx.isExhausted)
    }

    @Test
    @Timeout(1)
    fun shouldThrowTheException() {
        val step = SimpleStep<Long, Long>("", null) { _ -> throw RuntimeException("This is an error") }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = -1L)

        Assertions.assertThrows(RuntimeException::class.java) {
            runBlocking {
                step.execute(ctx)
            }
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        Assertions.assertFalse(ctx.isExhausted)
    }
}
