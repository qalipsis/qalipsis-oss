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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
internal class FilterStepTest {

    @Test
    @Timeout(1)
    fun `should forward input when input matches`() = runBlockingTest {
        val step = FilterStep<Long>("", null) { value -> value > 0 }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 1L)

        step.execute(ctx)
        val output = ctx.consumeOutputValue()

        Assertions.assertEquals(1L, output)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(1)
    fun `should close output when input does not match`() = runBlockingTest {
        val step = FilterStep<Long>("", null) { value -> value > 0 }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = -1L)

        step.execute(ctx)

        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
