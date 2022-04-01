package io.qalipsis.api.context

/**
 * Representation of an error in a step execution.
 *
 * @author Eric Jessé
 */
data class StepError(val message: String, var stepName: String = "") {
    constructor(cause: Throwable, stepName: String = "") : this(cause.message?.let {
        if (it.length > 1000) {
            it.take(1000) + "... (too long messages are truncated)"
        } else {
            it
        }
    } ?: "<No message>", stepName)
}
