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
 *
 * Execution profile strategy to increase the volume of minions in a constant pace.
 *
 * The global speed factor applies number of of minions pro launch, reducing or increasing it.
 *
 * @author Eric Jessé
 *
 * @property periodMs the period to apply between each launch, in milliseconds.
 * @property minionsCountProLaunchAtStart
 * @property multiplier
 * @property maxMinionsCountProLaunch
 */
class ProgressiveVolumeExecutionProfile(
    private val periodMs: Long, private val minionsCountProLaunchAtStart: Int,
    private val multiplier: Double, private val maxMinionsCountProLaunch: Int
) : ExecutionProfile {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        ProgressiveVolumeExecutionProfileIterator(
            periodMs, minionsCountProLaunchAtStart, multiplier * speedFactor,
            maxMinionsCountProLaunch, totalMinionsCount
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProgressiveVolumeExecutionProfile

        if (periodMs != other.periodMs) return false
        if (minionsCountProLaunchAtStart != other.minionsCountProLaunchAtStart) return false
        if (multiplier != other.multiplier) return false
        if (maxMinionsCountProLaunch != other.maxMinionsCountProLaunch) return false

        return true
    }

    override fun hashCode(): Int {
        var result = periodMs.hashCode()
        result = 31 * result + minionsCountProLaunchAtStart
        result = 31 * result + multiplier.hashCode()
        result = 31 * result + maxMinionsCountProLaunch
        return result
    }

    inner class ProgressiveVolumeExecutionProfileIterator(
        private val periodMs: Long, minionsCountProLaunchAtStart: Int,
        private val multiplier: Double,
        private val maxMinionsCountProLaunch: Int, totalMinionsCount: Int
    ) :
        ExecutionProfileIterator {

        private var nextVolume = minionsCountProLaunchAtStart

        private var remainingMinions = totalMinionsCount

        override fun next(): MinionsStartingLine {
            val result = MinionsStartingLine(nextVolume, periodMs)
            remainingMinions -= nextVolume
            nextVolume =
                (nextVolume * multiplier).toInt().coerceAtMost(maxMinionsCountProLaunch).coerceAtMost(remainingMinions)
            return result
        }

        override fun hasNext(): Boolean {
            return remainingMinions > 0
        }
    }
}

/**
 * Increase the volume of minions to start at a constant pace.
 */
fun ExecutionProfileSpecification.more(
    periodMs: Long, minionsCountProLaunchAtStart: Int, multiplier: Double,
    maxMinionsCountProLaunch: Int
) {
    strategy(ProgressiveVolumeExecutionProfile(periodMs, minionsCountProLaunchAtStart, multiplier, maxMinionsCountProLaunch))
}
