package io.evolue.api.rampup

import io.evolue.api.RampUpSpecification
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy
import io.evolue.core.factory.orchestration.rampup.RampUpStrategyIterator

/**
 *
 * Ramp-up strategy on constant number of minions launched at an accelerating pace.
 *
 * @author Eric Jessé
 */
class AcceleratingRampUp(private val startPeriodMs: Long, private val accelerator: Double,
                         private val minPeriodMs: Long, private val minionsCountProLaunch: Int) : RampUpStrategy {

    override fun iterator(totalMinionsCount: Int) =
        AcceleratingRampUpIterator(startPeriodMs, accelerator, minPeriodMs, minionsCountProLaunch, totalMinionsCount)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AcceleratingRampUp

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

    inner class AcceleratingRampUpIterator(startPeriodMs: Long, accelerator: Double, private val minPeriodMs: Long,
                                           private val minionsCountProLaunch: Int, totalMinionsCount: Int) :
        RampUpStrategyIterator {

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
    }
}

/**
 * Start a constant number of minions launched at an accelerating pace.
 */
fun RampUpSpecification.faster(startPeriodMs: Long, accelerator: Double, minPeriodMs: Long,
                               minionsCountProLaunch: Int) {
    strategy(AcceleratingRampUp(startPeriodMs, accelerator, minPeriodMs, minionsCountProLaunch))
}