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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ShelveStepSpecificationTest {

    @Test
    internal fun `should add shelve step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> Map<String, Any?> = { mapOf("value-1" to it + 1) }
        previousStep.shelve(specification)

        assertEquals(ShelveStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add shelve step with unique name as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.shelve("value-1")

        assertTrue(previousStep.nextSteps[0] is ShelveStepSpecification)
    }

    @Test
    internal fun `should add shelve step with unique name and specification as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.shelve("value-1") { input -> input.toString() }

        assertTrue(previousStep.nextSteps[0] is ShelveStepSpecification)
    }
}
