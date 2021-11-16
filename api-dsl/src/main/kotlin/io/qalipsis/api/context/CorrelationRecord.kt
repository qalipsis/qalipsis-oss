package io.qalipsis.api.context

/**
 *
 * Entity used to extract a correlation key from incoming values.
 *
 * The calculation of the correlation key can take the minion into account or not.
 *
 * @author Eric Jessé
 */
data class CorrelationRecord<I : Any?>(
    val minionId: MinionId,
    val stepId: StepId,
    val value: I
)