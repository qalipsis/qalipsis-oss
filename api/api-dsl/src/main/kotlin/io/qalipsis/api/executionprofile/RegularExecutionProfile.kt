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

package io.qalipsis.api.executionprofile

import io.qalipsis.api.scenario.ExecutionProfileSpecification

/**
 * Execution profile strategy to start a constant number of minions at a constant pace.
 *
 * The global speed factor applies on the constant period, reducing or increasing it.
 *
 * @author Eric Jessé
 */
data class RegularExecutionProfile(private val periodInMs: Long, private val minionsCountProLaunch: Int) :
    ExecutionProfile {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        RegularExecutionProfileIterator((periodInMs / speedFactor).toLong(), minionsCountProLaunch, totalMinionsCount)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegularExecutionProfile

        if (periodInMs != other.periodInMs) return false
        if (minionsCountProLaunch != other.minionsCountProLaunch) return false

        return true
    }

    override fun hashCode(): Int {
        var result = periodInMs.hashCode()
        result = 31 * result + minionsCountProLaunch
        return result
    }

    inner class RegularExecutionProfileIterator(
        private val periodInMs: Long, private val minionsCountProLaunch: Int,
        totalMinionsCount: Int
    ) : ExecutionProfileIterator {

        private var remainingMinions = totalMinionsCount

        override fun next(): MinionsStartingLine {
            val minionsCount = minionsCountProLaunch.coerceAtMost(remainingMinions)
            remainingMinions -= minionsCount
            return MinionsStartingLine(minionsCount, periodInMs)
        }

        override fun hasNext(): Boolean {
            return remainingMinions > 0
        }
    }
}

/**
 * Start a constant number of minions at a constant pace.
 */
fun ExecutionProfileSpecification.regular(periodMs: Long, minionsCountProLaunch: Int) {
    strategy(RegularExecutionProfile(periodMs, minionsCountProLaunch))
}
