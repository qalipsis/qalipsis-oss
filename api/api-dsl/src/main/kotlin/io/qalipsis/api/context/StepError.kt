package io.qalipsis.api.context

/**
 * Representation of an error in a step execution.
 *
 * @author Eric Jessé
 */
data class StepError(
        val cause: Throwable
)