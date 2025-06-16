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
 *
 * Execution profile strategy to let the user define his own strategy based upon the total count of minions to start.
 *
 * The global speed factor is passed to the ramp-up specifications.
 *
 * @author Eric Jessé
 */
class UserDefinedExecutionProfile(
    private val specification: (pastPeriodMs: Long, totalMinionsCount: Int, speedFactor: Double) -> MinionsStartingLine
) : ExecutionProfile {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        UserDefinedExecutionProfileIterator(totalMinionsCount, speedFactor, specification)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserDefinedExecutionProfile

        if (specification != other.specification) return false

        return true
    }

    override fun hashCode(): Int {
        return specification.hashCode()
    }

    inner class UserDefinedExecutionProfileIterator(
        private val totalMinionsCount: Int, private val speedFactor: Double,
        private val specification: (pastPeriodMs: Long, totalMinionsCount: Int, speedFactor: Double) -> MinionsStartingLine
    ) : ExecutionProfileIterator {

        private var pastPeriodMs = 0L

        private var remainingMinions = totalMinionsCount

        override fun next(): MinionsStartingLine {
            val result = specification(pastPeriodMs, totalMinionsCount, speedFactor)
            pastPeriodMs = result.offsetMs
            val minionsCount = result.count.coerceAtMost(remainingMinions)
            remainingMinions -= minionsCount

            return MinionsStartingLine(minionsCount, result.offsetMs)
        }

        override fun hasNext(): Boolean {
            return remainingMinions > 0
        }
    }
}

/**
 * Define a flexible strategy based upon past period and total count of minions to start.
 */
fun ExecutionProfileSpecification.define(
    specification: ((pastPeriodMs: Long, totalMinions: Int, speedFactor: Double) -> MinionsStartingLine)
) {
    strategy(UserDefinedExecutionProfile(specification))
}
