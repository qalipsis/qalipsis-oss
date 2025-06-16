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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class FlatMapStepSpecificationTest {

    @Test
    internal fun `should add flat map step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> Flow<Int> = { _ -> emptyFlow() }
        previousStep.flatMap(specification)

        assertEquals(FlatMapStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add default flat map step as next`() {
        val previousStep = DummyStepSpecification().map { arrayOf(it) }
        previousStep.flatten()

        assertTrue(previousStep.nextSteps[0] is FlatMapStepSpecification)
    }
}
