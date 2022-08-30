/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
