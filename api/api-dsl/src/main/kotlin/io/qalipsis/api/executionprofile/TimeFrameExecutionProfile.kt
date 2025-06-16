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
 * Execution profile strategy to start an adaptive count of minions in a limited time frame.
 *
 * The global speed factor applies on the duration of the time frame and period between launches, increasing or decreasing them.
 *
 * @author Eric Jessé
 */
data class TimeFrameExecutionProfile(private val periodInMs: Long, private val timeFrameInMs: Long) : ExecutionProfile {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        TimeFrameExecutionProfileIterator(
            (periodInMs / speedFactor).toLong(), (timeFrameInMs / speedFactor).toLong(),
            totalMinionsCount
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimeFrameExecutionProfile

        if (periodInMs != other.periodInMs) return false
        if (timeFrameInMs != other.timeFrameInMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = periodInMs.hashCode()
        result = 31 * result + timeFrameInMs.hashCode()
        return result
    }


    inner class TimeFrameExecutionProfileIterator(
        private val periodInMs: Long, timeFrameInMs: Long,
        totalMinionsCount: Int
    ) : ExecutionProfileIterator {

        private var remainingMinions = totalMinionsCount

        private val numberOfLaunches = timeFrameInMs / periodInMs

        private val minionsCountPerLaunch = Math.ceil(totalMinionsCount.toDouble() / numberOfLaunches).toInt()

        override fun next(): MinionsStartingLine {
            val minionsCount = minionsCountPerLaunch.coerceAtMost(remainingMinions)
            remainingMinions -= minionsCount
            return MinionsStartingLine(minionsCount, periodInMs)
        }

        override fun hasNext(): Boolean {
            return remainingMinions > 0
        }
    }
}

/**
 * Start an adaptive count of minions in a limited time frame.
 */
fun ExecutionProfileSpecification.timeframe(periodInMs: Long, timeFrameInMs: Long) {
    strategy(TimeFrameExecutionProfile(periodInMs, timeFrameInMs))
}
