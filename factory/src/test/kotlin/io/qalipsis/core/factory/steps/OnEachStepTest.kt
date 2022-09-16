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

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 *
 * @author Eric Jessé
 */
internal class OnEachStepTest {

    @Test
    @Timeout(1)
    fun `should execute statement`() = runBlockingTest {
        val collected = mutableListOf<Int>()
        val step = OnEachStep<Int>("", null) { collected.add(it) }
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 123)

        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals(output, 123)
        assertThat(collected).all {
            hasSize(1)
            index(0).isEqualTo(123)
        }
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
