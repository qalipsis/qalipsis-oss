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
 * Ramp-up strategy on constant number of minions launched at an accelerating pace.
 *
 * The global speed factor applies on the accelerator factor, making the periods reducing faster or slower.
 *
 * @author Eric Jessé
 *
 * @property startPeriodMs period between launch to apply at start, in milliseconds.
 * @property accelerator accelerator factor to reduce the period at each launch.
 * @property minPeriodMs the minimal period between launches, in milliseconds.
 * @property minionsCountProLaunch the number of minions to start at each launch.
 */
class AcceleratingExecutionProfile(
    private val startPeriodMs: Long, private val accelerator: Double,
    private val minPeriodMs: Long, private val minionsCountProLaunch: Int
) : ExecutionProfile {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        AcceleratingExecutionProfileIterator(
            startPeriodMs, accelerator * speedFactor,
            minPeriodMs, minionsCountProLaunch, totalMinionsCount
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AcceleratingExecutionProfile

        if (startPeriodMs != other.startPeriodMs) return false
        if (accelerator != other.accelerator) return false
        if (minPeriodMs != other.minPeriodMs) return false
        if (minionsCountProLaunch != other.minionsCountProLaunch) return false

        return true
    }

    override fun hashCode(): Int {
        var result = startPeriodMs.hashCode()
        result = 31 * result + accelerator.hashCode()
        result = 31 * result + minPeriodMs.hashCode()
        result = 31 * result + minionsCountProLaunch
        return result
    }

    inner class AcceleratingExecutionProfileIterator(
        startPeriodMs: Long, accelerator: Double, private val minPeriodMs: Long,
        private val minionsCountProLaunch: Int, totalMinionsCount: Int
    ) : ExecutionProfileIterator {

        private var nextPeriod = startPeriodMs

        private var divider = (1 / accelerator).coerceAtLeast(1E-12)

        private var remainingMinions = totalMinionsCount

        override fun next(): MinionsStartingLine {
            val minionsCount = minionsCountProLaunch.coerceAtMost(remainingMinions)
            remainingMinions -= minionsCount

            val result = MinionsStartingLine(minionsCount, nextPeriod)
            nextPeriod = (nextPeriod * divider).toLong().coerceAtLeast(minPeriodMs)

            return result
        }

        override fun hasNext(): Boolean {
            return remainingMinions > 0
        }
    }
}

/**
 * Start a constant number of minions launched at an accelerating pace.
 */
fun ExecutionProfileSpecification.faster(
    startPeriodMs: Long, accelerator: Double, minPeriodMs: Long,
    minionsCountProLaunch: Int
) {
    strategy(AcceleratingExecutionProfile(startPeriodMs, accelerator, minPeriodMs, minionsCountProLaunch))
}
