package io.evolue.api.rampup

import io.evolue.api.RampUpSpecification
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy
import io.evolue.core.factory.orchestration.rampup.RampUpStrategyIterator

/**
 *
 * Ramp-up Strategy to increase the volume of minions in a constant pace.
 *
 * @author Eric Jessé
 */
class ProgressiveVolumeRampUp(private val periodMs: Long, private val minionsCountProLaunchAtStart: Int,
                              private val multiplier: Double, private val maxMinionsCountProLaunch: Int) :
    RampUpStrategy {

    override fun iterator(totalMinionsCount: Int) =
        ProgressiveVolumeRampUpIterator(periodMs, minionsCountProLaunchAtStart, multiplier, maxMinionsCountProLaunch,
            totalMinionsCount)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProgressiveVolumeRampUp

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

    inner class ProgressiveVolumeRampUpIterator(private val periodMs: Long, minionsCountProLaunchAtStart: Int,
                                                private val multiplier: Double,
                                                private val maxMinionsCountProLaunch: Int, totalMinionsCount: Int) :
        RampUpStrategyIterator {

        private var nextVolume = minionsCountProLaunchAtStart

        private var remainingMinions = totalMinionsCount

        override fun next(): MinionsStartingLine {
            val result = MinionsStartingLine(nextVolume, periodMs)
            remainingMinions -= nextVolume
            nextVolume =
                (nextVolume * multiplier).toInt().coerceAtMost(maxMinionsCountProLaunch).coerceAtMost(remainingMinions)
            return result
        }

    }
}


/**
 * Increase the volume of minions to start at a constant pace.
 */
fun RampUpSpecification.more(periodMs: Long, minionsCountProLaunchAtStart: Int, multiplier: Double,
                             maxMinionsCountProLaunch: Int) {
    strategy(ProgressiveVolumeRampUp(periodMs, minionsCountProLaunchAtStart, multiplier, maxMinionsCountProLaunch))
}