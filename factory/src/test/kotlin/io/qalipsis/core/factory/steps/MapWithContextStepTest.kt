package io.qalipsis.core.factory.steps

import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class MapWithContextStepTest {

    @Test
    @Timeout(1)
    fun `should simply forward with default step`() = runBlockingTest {
        val step = MapWithContextStep<Int, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1)

        step.execute(ctx)
        val output = (ctx.output as Channel).receive()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)

    }

    @Test
    @Timeout(1)
    fun `should apply mapping`() = runBlockingTest {
        val step =
            MapWithContextStep<Int, String>("", null) { context, value -> context.minionId + "-" + value.toString() }
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        step.execute(ctx)
        val output = (ctx.output as Channel).receive()
        assertEquals(ctx.minionId + "-1", output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}