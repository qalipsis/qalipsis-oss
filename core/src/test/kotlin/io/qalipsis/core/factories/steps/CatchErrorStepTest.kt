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
internal class CatchErrorStepTest {

    @Test
    @Timeout(1)
    fun shouldIgnoreStepWhenNoError() {
        val reference = AtomicInteger(0)
        val step = CatchErrorStep<Any>("") { _ -> reference.incrementAndGet() }
        val ctx = createStepContext<Any, Any>()

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertEquals(0, reference.get())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(1)
    fun shouldExecuteStepWhenErrors() {
        val reference = AtomicInteger(0)
        val step = CatchErrorStep<Any>("") { _ -> reference.incrementAndGet() }
        val ctx = createStepContext<Any, Any>(errors = mutableListOf(StepError(RuntimeException(""))))

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertEquals(1, reference.get())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
