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
        val output = ctx.consumeOutputValue()
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
        val output = ctx.consumeOutputValue()
        assertEquals(ctx.minionId + "-1", output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}