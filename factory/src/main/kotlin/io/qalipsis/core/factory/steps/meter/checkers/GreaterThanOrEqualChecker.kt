/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.steps.meter.checkers

import io.qalipsis.api.lang.supplyUnless
import io.qalipsis.core.factory.steps.meter.MeterAssertionViolation

/**
 * Asserts the value is greater than or equal to the expected threshold, using the "greater than" and "equals" operators.
 *
 * @param threshold the reference value used in comparison
 *
 * @author Francisca Eze
 */
class GreaterThanOrEqualChecker<T : Comparable<T>>(
    private val valueName: String,
    private val threshold: T
) : ValueChecker<T> {

    override fun check(value: T): MeterAssertionViolation? {
        return supplyUnless(value >= threshold) {
            MeterAssertionViolation("The $valueName is $value but should be greater than or equal to $threshold")
        }
    }
}