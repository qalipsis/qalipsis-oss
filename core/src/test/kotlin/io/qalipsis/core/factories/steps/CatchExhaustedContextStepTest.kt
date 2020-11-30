package io.qalipsis.core.factories.steps

import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
internal class CatchExhaustedContextStepTest {

    @Test
    @Timeout(1)
    fun shouldIgnoreBlockWhenContextIsNotExhausted() {
        val reference = AtomicInteger(0)
        val step = CatchExhaustedContextStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = StepTestHelper.createStepContext<Any?, Int>(input = 123)

        val result = runBlocking {
            step.execute(ctx)
            (ctx.output as Channel<Int>).receive()
        }
        Assertions.assertEquals(0, reference.get())
        Assertions.assertEquals(123, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

    }

    @Test
    @Timeout(1)
    fun shouldExecuteStepWhenContextIsExhausted() {
        val reference = AtomicInteger(0)
        val step = CatchExhaustedContextStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = StepTestHelper.createStepContext<Any?, Int>(input = 456, exhausted = true)

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertEquals(1, reference.get())
        Assertions.assertTrue((ctx.output as Channel).isEmpty)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
