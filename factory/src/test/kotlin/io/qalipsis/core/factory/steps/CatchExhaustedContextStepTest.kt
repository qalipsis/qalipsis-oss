package io.qalipsis.core.factory.steps

import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
internal class CatchExhaustedContextStepTest {

    @Test
    @Timeout(1)
    fun `should ignore block when context is not exhausted`() = runBlockingTest {
        val reference = AtomicInteger(0)
        val step = CatchExhaustedContextStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = StepTestHelper.createStepContext<Any?, Int>(input = 123)

        step.execute(ctx)
        val result = (ctx.output as Channel<Int>).receive()

        Assertions.assertEquals(0, reference.get())
        Assertions.assertEquals(123, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

    }

    @Test
    @Timeout(1)
    fun `should execute step when context is exhausted`() = runBlockingTest {
        val reference = AtomicInteger(0)
        val step = CatchExhaustedContextStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = StepTestHelper.createStepContext<Any?, Int>(input = 456, isExhausted = true)

        step.execute(ctx)

        Assertions.assertEquals(1, reference.get())
        Assertions.assertTrue((ctx.output as Channel).isEmpty)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
