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

package io.qalipsis.api.rampup

import io.qalipsis.api.scenario.RampUpSpecification

/**
 * Ramp-up Strategy to start a constant number of minions at a constant pace.
 *
 * The global speed factor applies on the constant period, reducing or increasing it.
 *
 * @author Eric Jessé
 */
data class RegularRampUp(private val periodInMs: Long, private val minionsCountProLaunch: Int) : RampUpStrategy {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        RegularRampUpIterator((periodInMs / speedFactor).toLong(), minionsCountProLaunch, totalMinionsCount)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegularRampUp

        if (periodInMs != other.periodInMs) return false
        if (minionsCountProLaunch != other.minionsCountProLaunch) return false

        return true
    }

    override fun hashCode(): Int {
        var result = periodInMs.hashCode()
        result = 31 * result + minionsCountProLaunch
        return result
    }

    inner class RegularRampUpIterator(
        private val periodInMs: Long, private val minionsCountProLaunch: Int,
        totalMinionsCount: Int
    ) : RampUpStrategyIterator {

        private var remainingMinions = totalMinionsCount

        override fun next(): MinionsStartingLine {
            val minionsCount = minionsCountProLaunch.coerceAtMost(remainingMinions)
            remainingMinions -= minionsCount
            return MinionsStartingLine(minionsCount, periodInMs)
        }
    }
}

/**
 * Start a constant number of minions at a constant pace.
 */
fun RampUpSpecification.regular(periodMs: Long, minionsCountProLaunch: Int) {
    strategy(RegularRampUp(periodMs, minionsCountProLaunch))
}
