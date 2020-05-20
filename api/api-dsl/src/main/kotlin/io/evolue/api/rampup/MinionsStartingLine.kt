package io.evolue.api.rampup

/**
 * Ramp-up of minions on a scenario are defined as a sequence of starts, which are described by a [MinionsStartingLine].
 *
 * @see io.evolue.core.factory.orchestration.rampup.RampUpStrategy
 * @see io.evolue.core.factory.orchestration.rampup.RampUpStrategyIterator
 *
 * @author Eric Jessé
 */
data class MinionsStartingLine(
    /**
     * Number of minions to start on the next starting line.
     */
    val count: Int,

    /**
     * Offset of the start, related to the first start of the sequence.
     */
    val offsetMs: Long
)