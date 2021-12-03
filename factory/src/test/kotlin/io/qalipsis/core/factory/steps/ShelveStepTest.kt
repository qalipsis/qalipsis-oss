package io.qalipsis.core.factory.steps

import io.mockk.coJustRun
import io.mockk.confirmVerified
import io.mockk.slot
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ShelveStepTest {

    @Test
    internal fun `should push all the values to the shared state registry`() = runBlockingTest {
        val capturedValues = slot<Map<SharedStateDefinition, Any?>>()
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
           coJustRun { set(capture(capturedValues)) }
        }
        val step = ShelveStep<Long>("", sharedStateRegistry) { input ->
            mapOf("value-1" to input + 1, "value-2" to "My Value", "value-3" to null)
        }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 123L)

        step.execute(ctx)
        val output = (ctx.output as Channel).receive()
        assertEquals(123L, output)

        assertEquals(mapOf(
            SharedStateDefinition("my-minion", "value-1") to 124L,
            SharedStateDefinition("my-minion", "value-2") to "My Value",
            SharedStateDefinition("my-minion", "value-3") to null
        ), capturedValues.captured)
        coVerifyOnce {
            sharedStateRegistry.set(any<Map<SharedStateDefinition, Any?>>())
        }
        confirmVerified(sharedStateRegistry)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
