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

package io.qalipsis.core.factory.steps.meter.checkers

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.core.factory.steps.meter.MeterAssertionViolation
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * @author Francisca Eze
 */
internal class GreaterThanOrEqualsCheckerTest {

    @ParameterizedTest
    @ValueSource(doubles = [24.0, 32.0, 29.0, 81.0, 24.01])
    fun `should not return any exception when the condition is successful` (value: Double) {
        // given
        val checker = GreaterThanOrEqualChecker(24.0)

        // when + then
        assertNull(checker.check(value))
    }

    @ParameterizedTest
    @ValueSource(doubles = [4.0, 17.0, 22.0, 22.99, -23.0])
    fun `should return an exception when value is less than the threshold` (value: Double) {
        // given
        val checker = GreaterThanOrEqualChecker(23.0)

        // when
        val exception = checker.check(value)

        // then
        assertThat(exception).isNotNull().all {
            isInstanceOf(MeterAssertionViolation::class.java)
            prop(MeterAssertionViolation::message).isEqualTo("Value should be greater than or equal to 23.0")
        }

    }
}