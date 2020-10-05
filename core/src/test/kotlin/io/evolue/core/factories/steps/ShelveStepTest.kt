package io.evolue.core.factories.steps

import io.evolue.api.states.SharedStateDefinition
import io.evolue.api.states.SharedStateRegistry
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.StepTestHelper
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.slot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
internal class ShelveStepTest {

    @Test
    internal fun `should push all the values to the shared state registry`() {
        val capturedValues = slot<Map<SharedStateDefinition, Any?>>()
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            every { set(capture(capturedValues)) } returns Unit
        }
        val step = ShelveStep<Long>("", sharedStateRegistry) { input ->
            mapOf("value-1" to input + 1, "value-2" to "My Value", "value-3" to null)
        }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 123L)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(123L, output)
        }

        assertEquals(mapOf(
            SharedStateDefinition("my-minion", "value-1") to 124L,
            SharedStateDefinition("my-minion", "value-2") to "My Value",
            SharedStateDefinition("my-minion", "value-3") to null
        ), capturedValues.captured)
        verifyOnce {
            sharedStateRegistry.set(any<Map<SharedStateDefinition, Any?>>())
        }
        confirmVerified(sharedStateRegistry)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
