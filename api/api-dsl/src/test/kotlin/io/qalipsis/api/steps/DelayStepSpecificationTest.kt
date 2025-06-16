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

import io.qalipsis.api.exceptions.InvalidSpecificationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class DelayStepSpecificationTest {

    @Test
    internal fun `should add delay decorator with duration as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.delay(Duration.ofMillis(123))

        assertEquals(DelayStepSpecification<Int>(Duration.ofMillis(123)), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add delay decorator with milliseconds as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.delay(123)

        assertEquals(DelayStepSpecification<Int>(Duration.ofMillis(123)), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should generate error when the duration is zero`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(Duration.ZERO)
        }
    }

    @Test
    internal fun `should generate error when the duration is negative`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(Duration.ofMillis(-1))
        }
    }

    @Test
    internal fun `should generate error when the milliseconds are zero`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(0)
        }
    }

    @Test
    internal fun `should generate error when the milliseconds are negative`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(-1)
        }
    }

}