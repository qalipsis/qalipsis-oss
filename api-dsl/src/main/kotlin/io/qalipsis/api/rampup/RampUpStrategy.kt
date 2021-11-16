package io.qalipsis.api.rampup

/**
 *
 * [RampUpStrategy] is an accessor to a [RampUpStrategyIterator]. The [RampUpStrategy] is part of the definition of the
 * scenario and defines the pace to start the minions.
 *
 * @author Eric Jessé
 */
interface RampUpStrategy {

    /**
     * Generates a new [RampUpStrategyIterator] to define a new sequence of starts.
     *
     * @param totalMinionsCount the total number of minions that will be started for the scenario.
     * @param speedFactor the factor to accelerate (when greater than 1) or slower (between 0 and 1) the ramp-up.
     */
    fun iterator(totalMinionsCount: Int, speedFactor: Double): RampUpStrategyIterator
}
