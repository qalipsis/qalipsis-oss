/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.steps

import io.mockk.coEvery
import io.mockk.confirmVerified
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
internal class UnshelveStepTest {

    @Test
    internal fun `should get all the values from the shared state registry`() = runBlockingTest {
        val values = mapOf("value-1" to 123L, "value-2" to "My Value", "value-3" to null)
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            coEvery { get(any<Iterable<SharedStateDefinition>>()) } returns values
        }

        val step = UnshelveStep<Long>("", sharedStateRegistry, listOf("value-1", "value-2", "value-3"), false)
        val ctx = StepTestHelper.createStepContext<Long, Pair<Long, Map<String, Any?>>>(input = 123L)

        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals(output, 123L to values)

        coVerifyOnce {
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
    internal fun `should remove all the values from the shared state registry`() = runBlockingTest {
        val values = mapOf("value-1" to 123L, "value-2" to "My Value", "value-3" to null)
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            coEvery { remove(any()) } returns values
        }

        val step = UnshelveStep<Long>("", sharedStateRegistry, listOf("value-1", "value-2", "value-3"), true)
        val ctx = StepTestHelper.createStepContext<Long, Pair<Long, Map<String, Any?>>>(input = 123L)

        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals(output, 123L to values)

        coVerifyOnce {
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
    internal fun `should get the value from the shared state registry`() = runBlockingTest {
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            coEvery { get<String>(any()) } returns "The value"
        }

        val step = SingularUnshelveStep<Long, String>("", sharedStateRegistry, "value-1", false)
        val ctx = StepTestHelper.createStepContext<Long, Pair<Long, String?>>(input = 123L)

        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals(output, 123L to "The value")

        coVerifyOnce {
            sharedStateRegistry.get<String>(SharedStateDefinition("my-minion", "value-1"))
        }
        confirmVerified(sharedStateRegistry)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }


    @Test
    internal fun `should remove the value from the shared state registry`() = runBlockingTest {
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk {
            coEvery { remove<String>(any()) } returns "The value"
        }

        val step = SingularUnshelveStep<Long, String>("", sharedStateRegistry, "value-1", true)
        val ctx = StepTestHelper.createStepContext<Long, Pair<Long, String?>>(input = 123L)

        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals(output, 123L to "The value")

        coVerifyOnce {
            sharedStateRegistry.remove<String>(SharedStateDefinition("my-minion", "value-1"))
        }
        confirmVerified(sharedStateRegistry)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
