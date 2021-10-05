package io.qalipsis.core.exceptions

/**
 *
 * Exception to throw to force the [Runner] to mark the step execution as a failure.
 *
 * @author Eric Jessé
 */
internal class StepExecutionException(cause: Throwable? = null) : RuntimeException(cause)