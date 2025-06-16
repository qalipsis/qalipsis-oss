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
internal class PaceStepSpecificationTest {

    @Test
    internal fun `should add pace step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (pastPeriodMs: Long) -> Long = { _ -> 10 }
        previousStep.pace(specification)

        assertEquals(PaceStepSpecification<Int>(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add accelerating pace step as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.acceleratingPace(20, 2.0, 4)

        assertTrue(previousStep.nextSteps[0] is PaceStepSpecification)
        val paceStepSpecification = (previousStep.nextSteps[0] as PaceStepSpecification).specification

        // At start it should be 20.
        assertEquals(20, paceStepSpecification(0))
        // Each next value should be half of the previous one.
        assertEquals(5, paceStepSpecification(10))
        // The smallest value should be 4.
        assertEquals(4, paceStepSpecification(1))
    }

    @Test
    internal fun `should add constant pace step as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.constantPace(20)

        assertTrue(previousStep.nextSteps[0] is PaceStepSpecification)
        val paceStepSpecification = (previousStep.nextSteps[0] as PaceStepSpecification).specification

        assertEquals(20, paceStepSpecification(0))
        assertEquals(20, paceStepSpecification(10))
        assertEquals(20, paceStepSpecification(1))
    }
}