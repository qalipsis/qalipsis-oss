/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class UnshelveStepSpecificationTest {

    @Test
    internal fun `should add unshelve step as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.unshelve("value-1", "value-2", "value-3")

        assertEquals(
            UnshelveStepSpecification<Int, Pair<Int, Map<String, Any?>>>(
                listOf("value-1", "value-2", "value-3"),
                false, false
            ),
            previousStep.nextSteps[0]
        )
    }

    @Test
    internal fun `should add unshelve step with deletion as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.unshelveAndDelete("value-1", "value-2", "value-3")

        assertEquals(
            UnshelveStepSpecification<Int, Pair<Int, Map<String, Any?>>>(
                listOf("value-1", "value-2", "value-3"),
                true, false
            ),
            previousStep.nextSteps[0]
        )
    }

    @Test
    internal fun `should add unshelve step with unique name as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.unshelve<Int, Double>("value-1")

        assertEquals(
            UnshelveStepSpecification<Int, Pair<Int, Double>>(listOf("value-1"), true, true),
            previousStep.nextSteps[0]
        )
    }

    @Test
    internal fun `should add unshelve step with unique name and deletion as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.unshelve<Int, Double>("value-1", false)

        assertEquals(
            UnshelveStepSpecification<Int, Pair<Int, Double>>(listOf("value-1"), false, true),
            previousStep.nextSteps[0]
        )
    }
}
