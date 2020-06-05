package io.evolue.api.rampup

import io.evolue.api.scenario.RampUpSpecification
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy
import io.evolue.core.factory.orchestration.rampup.RampUpStrategyIterator

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
class AcceleratingRampUp(private val startPeriodMs: Long, private val accelerator: Double,
                         private val minPeriodMs: Long, private val minionsCountProLaunch: Int) : RampUpStrategy {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        AcceleratingRampUpIterator(startPeriodMs, accelerator * speedFactor,
            minPeriodMs, minionsCountProLaunch, totalMinionsCount)

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