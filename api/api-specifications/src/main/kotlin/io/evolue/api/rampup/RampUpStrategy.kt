package io.evolue.core.factory.orchestration.rampup

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
     */
    fun iterator(totalMinionsCount: Int): RampUpStrategyIterator
}