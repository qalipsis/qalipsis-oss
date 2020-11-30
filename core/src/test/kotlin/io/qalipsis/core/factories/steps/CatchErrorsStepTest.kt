package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepError
import io.qalipsis.test.steps.StepTestHelper.createStepContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
internal class CatchErrorsStepTest {

    @Test
    @Timeout(3)
    fun shouldIgnoreBlockWhenNoError() {
        val reference = AtomicInteger(0)
        val step = CatchErrorsStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = createStepContext<Int, Int>(input = 123)

        val result = runBlocking {
            step.execute(ctx)
            (ctx.output as Channel<Int>).receive()
        }
        Assertions.assertEquals(0, reference.get())
        Assertions.assertEquals(123, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(3)
    fun shouldExecuteBlockWhenErrors() {
        val reference = AtomicInteger(0)
        val step = CatchErrorsStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = createStepContext<Int, Int>(input = 456, errors = mutableListOf(StepError(RuntimeException(""))))

        val result = runBlocking {
            step.execute(ctx)
            (ctx.output as Channel<Int>).receive()
        }
        Assertions.assertEquals(1, reference.get())
        Assertions.assertEquals(456, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

}
