package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepError
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Eric Jessé
 */
internal class ValidationStepTest {

    @Test
    @Timeout(1)
    fun shouldReturnsTheValidationErrors() = runBlockingTest {
        val processedValue = AtomicLong()
        val step = ValidationStep<Long>("", null) {
            processedValue.set(it)
            listOf(StepError(RuntimeException("Error 1")), StepError(RuntimeException("Error 2")))
        }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 123L)

        step.execute(ctx)
        val output = (ctx.output as Channel).receive()
        assertEquals(123L, output)

        assertEquals(123L, processedValue.get())
        assertEquals(listOf("Error 1", "Error 2"), ctx.errors.map { error -> error.message })
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

}
