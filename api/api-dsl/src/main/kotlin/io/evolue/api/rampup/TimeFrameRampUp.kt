package io.evolue.api.rampup

import io.evolue.api.RampUpSpecification
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy
import io.evolue.core.factory.orchestration.rampup.RampUpStrategyIterator

/**
 * Ramp-up Strategy to start an adaptive count of minions in a limited time frame.
 *
 * The global speed factor applies on the duration of the time frame and period between launches, increasing or decreasing them.
 *
 * @author Eric Jessé
 */
data class TimeFrameRampUp(private val periodInMs: Long, private val timeFrameInMs: Long) : RampUpStrategy {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        TimeFrameRampUpIterator((periodInMs / speedFactor).toLong(), (timeFrameInMs / speedFactor).toLong(),
            totalMinionsCount)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimeFrameRampUp

        if (periodInMs != other.periodInMs) return false
        if (timeFrameInMs != other.timeFrameInMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = periodInMs.hashCode()
        result = 31 * result + timeFrameInMs.hashCode()
        return result
    }


    inner class TimeFrameRampUpIterator(private val periodInMs: Long, timeFrameInMs: Long,
                                        totalMinionsCount: Int) : RampUpStrategyIterator {

        private var remainingMinions = totalMinionsCount

        private val numberOfLaunches = timeFrameInMs / periodInMs

        private val minionsCountPerLaunch = Math.ceil(totalMinionsCount.toDouble() / numberOfLaunches).toInt()

        override fun next(): MinionsStartingLine {
            val minionsCount = minionsCountPerLaunch.coerceAtMost(remainingMinions)
            remainingMinions -= minionsCount
            return MinionsStartingLine(minionsCount, periodInMs)
        }
    }
}

/**
 * Start an adaptive count of minions in a limited time frame.
 */
fun RampUpSpecification.timeframe(periodInMs: Long, timeFrameInMs: Long) {
    strategy(TimeFrameRampUp(periodInMs, timeFrameInMs))
}