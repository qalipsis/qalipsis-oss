package io.qalipsis.api.context

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Context of a unique execution of a minion on a step.
 * When the same minion repeats the same step in a raw, several contexts should be used.
 *
 * @property campaignId Identifier of the test campaign owning the context
 * @property minionId Identifier of the Minion owning the context
 * @property scenarioId Identifier of the Scenario being executed
 * @property directedAcyclicGraphId Identifier of the DirectedAcyclicGraph being executed
 * @property previousStepId First ancestor step
 * @property stepId Step executing the context
 * @property stepType Nature of the executed step, if defined
 * @property stepFamily Family or plugin the executed step belongs, if defined
 * @property stepIterationIndex Index of the current iteration for the same step and context
 * @property isExhausted When set to true, the context can neither be used for a new iteration nor propagated
 * @property isTail Marks a context being the latest of the convey of a given minion
 * @property errors List of the errors, from current and previous steps
 * @property hasInput Indicates whether there is an input to consume in the context (when true)
 * @property generatedOutput Indicates whether at least one output value was generated (when true)
 * @property equivalentCompletionContext [CompletionContext] representing the values of this [StepContext]
 *
 */
interface StepContext<IN, OUT> : StepOutput<OUT>, MonitoringTags {
    val campaignId: CampaignId
    val minionId: MinionId
    val scenarioId: ScenarioId
    val previousStepId: StepId?
    val stepId: StepId
    var stepType: String?
    var stepFamily: String?
    val stepIterationIndex: Long
    var isExhausted: Boolean
    var isTail: Boolean
    val errors: List<StepError>
    val hasInput: Boolean
    val generatedOutput: Boolean

    val equivalentCompletionContext: CompletionContext

    /**
     * Receives a record from the previous step.
     */
    suspend fun receive(): IN

    /**
     * Locks the internal latch of the context.
     */
    suspend fun lock()

    /**
     * Waits for the internal latch of the context to be released.
     */
    suspend fun await()

    /**
     * Releases the internal latch of the context.
     */
    suspend fun release()

    /**
     * Creates the [StepContext] for the next step with ID [stepId] and initializes the input of the
     * new context with [input].
     */
    fun <T> next(input: OUT, stepId: StepId): StepContext<OUT, T>

    /**
     * Creates the [StepContext] for the next step with ID [stepId] without any input.
     */
    fun <T> next(stepId: StepId): StepContext<OUT, T>

    /**
     * Duplicates this [StepContext], specifying new channels and step iteration index.
     *
     * @param inputChannel new channel to use for the input - when none is specified, the one from this step is reused
     * @param outputChannel new channel to use for the output - when none is specified, the one from this step is reused
     * @param stepIterationIndex new index of iteration
     */
    fun duplicate(
        inputChannel: ReceiveChannel<IN>? = null,
        outputChannel: SendChannel<StepOutputRecord<OUT>>? = null,
        stepIterationIndex: Long = this.stepIterationIndex
    ): StepContext<IN, OUT>

    /**
     * Closes this [StepContext].
     */
    suspend fun close() {
        release()
    }

    /**
     * Container of an output record from a step.
     *
     * @property value value returned by the step
     * @property isTail indicates whether the value is the tail for the minion
     */
    data class StepOutputRecord<O>(val value: O, val isTail: Boolean)
}
