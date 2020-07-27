package io.evolue.core.factory.steps

import io.evolue.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
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
    fun shouldIgnoreStepWhenContextIsNotExhausted() {
        val reference = AtomicInteger(0)
        val step = CatchExhaustedContextStep<Any, Any>("") { _ -> reference.incrementAndGet() }
        val ctx = StepTestHelper.createStepContext<Any, Any>()

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertEquals(0, reference.get())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

    }

    @Test
    @Timeout(1)
    fun shouldExecuteStepWhenContextIsExhausted() {
        val reference = AtomicInteger(0)
        val step = CatchExhaustedContextStep<Any, Any>("") { _ -> reference.incrementAndGet() }
        val ctx = StepTestHelper.createStepContext<Any, Any>(exhausted = true)

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertEquals(1, reference.get())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}