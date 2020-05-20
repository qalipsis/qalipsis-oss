package io.evolue.api.context

/**
 *
 * @author Eric Jessé
 *
 */
data class CorrelationRecord<I : Any?>(
    val minionId: MinionId,
    val stepId: StepId,
    val value: I
)