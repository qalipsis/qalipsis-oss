package io.evolue.api.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.retry.RetryPolicy

/**
 * Part of scenario responsible for processing a record.
 *
 * A step can convert the record, use is as a source for requests, consume messages to provide records.
 *
 * @author Eric Jessé
 */
interface Step<I : Any?, O : Any?> {

    val id: StepId

    var retryPolicy: RetryPolicy?

    /**
     * Returns the list of next steps or an empty list if there is none.
     */
    val next: List<Step<O, *>>

    /**
     * Add a step to the collection of next ones.
     */
    fun addNext(nextStep: Step<O, *>)

    /**
     * Operation to execute just after the creation of the step.
     */
    suspend fun init() = Unit

    /**
     * Operation to execute when a campaign starts.
     */
    @Throws(Exception::class)
    suspend fun start() = Unit

    /**
     * Execute the operation implies by the step.
     */
    @Throws(Exception::class)
    suspend fun execute(context: StepContext<I, O>)

    /**
     * Operation to execute when a campaign ends.
     */
    suspend fun stop() = Unit

    /**
     * Operation to execute just before the destruction of the step.
     */
    suspend fun destroy() = Unit
}
