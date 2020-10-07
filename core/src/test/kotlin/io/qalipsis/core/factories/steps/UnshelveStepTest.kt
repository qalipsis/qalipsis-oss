package io.qalipsis.core.factories.steps

import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.StepTestHelper
import io.mockk.confirmVerified
import io.mockk.every
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
internal class UnshelveStepTest {

    @Test
    internal fun `should get all the values from the shared state registry`() {
        val values = mapOf("value-1" to 123L, "value-2" to "My Value", "value-3" to null)
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            every { get(any<Iterable<SharedStateDefinition>>()) } returns values
        }

        val step = UnshelveStep<Long>("", sharedStateRegistry, listOf("value-1", "value-2", "value-3"), false)
        val ctx = StepTestHelper.createStepContext<Long, Pair<Long, Map<String, Any?>>>(input = 123L)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(output, 123L to values)
        }

        verifyOnce {
            sharedStateRegistry.get(listOf(
                SharedStateDefinition("my-minion", "value-1"),
                SharedStateDefinition("my-minion", "value-2"),
                SharedStateDefinition("my-minion", "value-3")
            ))
        }
        confirmVerified(sharedStateRegistry)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }


    @Test
    internal fun `should remove all the values from the shared state registry`() {
        val values = mapOf("value-1" to 123L, "value-2" to "My Value", "value-3" to null)
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            every { remove(any<Iterable<SharedStateDefinition>>()) } returns values
        }

        val step = UnshelveStep<Long>("", sharedStateRegistry, listOf("value-1", "value-2", "value-3"), true)
        val ctx = StepTestHelper.createStepContext<Long, Pair<Long, Map<String, Any?>>>(input = 123L)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(output, 123L to values)
        }

        verifyOnce {
            sharedStateRegistry.remove(listOf(
                SharedStateDefinition("my-minion", "value-1"),
                SharedStateDefinition("my-minion", "value-2"),
                SharedStateDefinition("my-minion", "value-3")
            ))
        }
        confirmVerified(sharedStateRegistry)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    internal fun `should get the value from the shared state registry`() {
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            every { get<String>(any()) } returns "The value"
        }

        val step = SingularUnshelveStep<Long, String>("", sharedStateRegistry, "value-1", false)
        val ctx = StepTestHelper.createStepContext<Long, Pair<Long, String?>>(input = 123L)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(output, 123L to "The value")
        }

        verifyOnce {
            sharedStateRegistry.get<String>(SharedStateDefinition("my-minion", "value-1"))
        }
        confirmVerified(sharedStateRegistry)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }


    @Test
    internal fun `should remove the value from the shared state registry`() {
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            every { remove<String>(any()) } returns "The value"
        }

        val step = SingularUnshelveStep<Long, String>("", sharedStateRegistry, "value-1", true)
        val ctx = StepTestHelper.createStepContext<Long, Pair<Long, String?>>(input = 123L)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(output, 123L to "The value")
        }

        verifyOnce {
            sharedStateRegistry.remove<String>(SharedStateDefinition("my-minion", "value-1"))
        }
        confirmVerified(sharedStateRegistry)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
