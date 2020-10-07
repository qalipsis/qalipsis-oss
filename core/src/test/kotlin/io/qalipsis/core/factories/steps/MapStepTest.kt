package io.qalipsis.core.factories.steps

import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
internal class MapStepTest {

    @Test
    @Timeout(1)
    fun shouldSimplyForwardWithDefaultStep() {
        val step = MapStep<Int, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(1, output)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

    }

    @Test
    @Timeout(1)
    fun shouldApplyMapping() {
        val step = MapStep<Int, String>("", null) { value -> value.toString() }
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals("1", output)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
