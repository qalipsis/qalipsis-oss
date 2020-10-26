package io.qalipsis.api.rampup

import io.qalipsis.api.rampup.MinionsStartingLine

/**
 * [RampUpStrategyIterator] defines how fast the [io.qalipsis.api.orchestration.Minion]s has to be started to simulate the load
 * on a scenario.
 *
 * @author Eric Jessé
 */
interface RampUpStrategyIterator {

    /**
     * Defines the next starting line for the strategy.
     */
    fun next(): MinionsStartingLine
}
