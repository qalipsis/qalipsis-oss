package io.evolue.api.rampup

import io.evolue.api.RampUpSpecification
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy
import io.evolue.core.factory.orchestration.rampup.RampUpStrategyIterator

/**
 *
 * Ramp-up Strategy to let the user define his own strategy based upon the total count of minions to start.
 *
 * @author Eric Jessé
 */
class UserDefinedRampUp(
    private val specification: (pastPeriodMs: Long, totalMinionsCount: Int) -> MinionsStartingLine) :
    RampUpStrategy {

    override fun iterator(totalMinionsCount: Int) = UserDefinedRampUpIterator(totalMinionsCount, specification)

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

    inner class UserDefinedRampUpIterator(private val totalMinionsCount: Int,
                                          private val specification: (pastPeriodMs: Long, totalMinionsCount: Int) -> MinionsStartingLine) :
        RampUpStrategyIterator {

        private var pastPeriodMs = 0L

        private var remainingMinions = totalMinionsCount

        override fun next(): MinionsStartingLine {
            val result = specification(pastPeriodMs, totalMinionsCount)
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
fun RampUpSpecification.define(specification: ((pastPeriodMs: Long, totalMinions: Int) -> MinionsStartingLine)) {
    strategy(UserDefinedRampUp(specification))
}