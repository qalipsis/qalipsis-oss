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
 *
 * Ramp-up Strategy to let the user define his own strategy based upon the total count of minions to start.
 *
 * The global speed factor is passed to the ramp-up specifications.
 *
 * @author Eric Jessé
 */
class UserDefinedRampUp(
    private val specification: (pastPeriodMs: Long, totalMinionsCount: Int, speedFactor: Double) -> MinionsStartingLine
) :
    RampUpStrategy {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        UserDefinedRampUpIterator(totalMinionsCount, speedFactor, specification)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserDefinedRampUp

        if (specification != other.specification) return false

        return true
    }

    override fun hashCode(): Int {
        return specification.hashCode()
    }

    inner class UserDefinedRampUpIterator(
        private val totalMinionsCount: Int, private val speedFactor: Double,
        private val specification: (pastPeriodMs: Long, totalMinionsCount: Int, speedFactor: Double) -> MinionsStartingLine
    ) :
        RampUpStrategyIterator {

        private var pastPeriodMs = 0L

        private var remainingMinions = totalMinionsCount

        override fun next(): MinionsStartingLine {
            val result = specification(pastPeriodMs, totalMinionsCount, speedFactor)
            pastPeriodMs = result.offsetMs
            val minionsCount = result.count.coerceAtMost(remainingMinions)
            remainingMinions -= minionsCount

            return MinionsStartingLine(minionsCount, result.offsetMs)
        }
    }
}

/**
 * Define a flexible strategy based upon past period and total count of minions to start.
 */
fun RampUpSpecification.define(
    specification: ((pastPeriodMs: Long, totalMinions: Int, speedFactor: Double) -> MinionsStartingLine)
) {
    strategy(UserDefinedRampUp(specification))
}
