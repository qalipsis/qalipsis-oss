package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepError
import io.qalipsis.test.steps.StepTestHelper.createStepContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * @author Eric Jessé
 */
internal class CatchErrorsStepTest {

    @Test
    @Timeout(3)
    fun shouldIgnoreBlockWhenNoError() = runBlockingTest {
        val reference = AtomicInteger(0)
        val step = CatchErrorsStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = createStepContext<Int, Int>(input = 123)

        step.execute(ctx)
        val result = (ctx.output as Channel<Int>).receive()

        Assertions.assertEquals(0, reference.get())
        Assertions.assertEquals(123, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(3)
    fun shouldExecuteBlockWhenErrors() = runBlockingTest {
        val reference = AtomicInteger(0)
        val step = CatchErrorsStep<Int>("") { reference.incrementAndGet() }
        val ctx = createStepContext<Int, Int>(input = 456, errors = mutableListOf(StepError(RuntimeException(""))))

        step.execute(ctx)
        val result = (ctx.output as Channel<Int>).receive()

        Assertions.assertEquals(1, reference.get())
        Assertions.assertEquals(456, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

}
