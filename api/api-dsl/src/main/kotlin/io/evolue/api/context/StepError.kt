package io.evolue.api.context

/**
 * Representation of an error in a step execution.
 *
 * @author Eric Jessé
 */
class StepError(
    val cause: Throwable
)