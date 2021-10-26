package io.qalipsis.api.context

/**
 * Representation of an error in a step execution.
 *
 * @author Eric Jessé
 */
data class StepError(val message: String, var stepId: String = "") {
    constructor(cause: Throwable, stepId: String = "") : this(cause.message?.let {
        if (it.length > 1000) {
            it.take(1000) + "... (too long messages are truncated)"
        } else {
            it
        }
    } ?: "<No message>", stepId)
}
