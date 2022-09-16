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
        val output = ctx.consumeOutputValue()
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
