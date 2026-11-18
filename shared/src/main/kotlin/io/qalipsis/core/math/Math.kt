/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.core.math

import kotlin.math.pow
import kotlin.math.roundToInt


/**
 * Creates a rounded percentage representation of this number in regard of [total].
 */
fun Number.percentOf(total: Number): Double {
    return (100 * this.toDouble() / total.toDouble()).roundTwoDecimals()
}

/**
 * Round the double, using the specified number of decimal.
 */
fun Double.round(decimal: Int): Double {
    val factor = 10.0.pow(decimal)
    return (factor * this).roundToInt().toDouble() / factor
}

/**
 * Round the double.
 */
fun Double.roundTwoDecimals(): Double {
    return this.round(2)
}