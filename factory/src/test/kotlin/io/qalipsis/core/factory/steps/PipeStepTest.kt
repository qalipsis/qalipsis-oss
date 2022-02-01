package io.qalipsis.core.factory.steps

import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PipeStepTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    internal fun `should forward input to output`() = testCoroutineDispatcher.runTest {
        // given
        val step = PipeStep<Int>("")
        val ctx = StepTestHelper.createStepContext<Int, Int>(
            input = 1,
            campaignId = "my-campaign",
            scenarioId = "my-scenario",
            minionId = "my-minion",
            isTail = false
        )

        // when
        step.execute(ctx)

        //then
        val output = (ctx.output as Channel).receive()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}