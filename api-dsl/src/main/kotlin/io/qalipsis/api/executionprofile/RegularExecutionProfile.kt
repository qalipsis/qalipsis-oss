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
 * Execution profile strategy to start a constant number of minions at a constant pace.
 *
 * The global speed factor applies on the constant period, reducing or increasing it.
 *
 * @author Eric Jessé
 */
data class RegularExecutionProfile(private val periodInMs: Long, private val minionsCountProLaunch: Int) : ExecutionProfile {

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
