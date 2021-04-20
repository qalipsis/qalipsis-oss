package io.qalipsis.api.context

/**
 * Output for step related operations.
 *
 * @author Eric Jessé
 */
interface StepOutput<OUT> {

    /**
     * Adds an error into the context.
     */
    fun addError(error: StepError)

    /**
     * Sends a record to the next steps.
     */
    suspend fun send(element: OUT)

}
