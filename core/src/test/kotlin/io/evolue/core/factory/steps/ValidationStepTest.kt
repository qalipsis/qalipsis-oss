package io.evolue.core.factory.steps

import io.evolue.api.context.StepError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
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
    fun shouldReturnsTheValidationErrors() {
        val processedValue = AtomicLong()
        val step = ValidationStep<Long>("", null) {
            processedValue.set(it)
            listOf(StepError(RuntimeException("Error 1")), StepError(RuntimeException("Error 2")))
        }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 123L)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(output, 123L)
        }

        assertEquals(123L, processedValue.get())
        assertEquals(listOf("Error 1", "Error 2"), ctx.errors.map { error -> error.cause.message })
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

}