package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

interface StepContext<IN, OUT> : StepOutput<OUT> {
    /**
     * Identifier of the test campaign owning the context.
     */
    val campaignId: CampaignId

    /**
     * Identifier of the Minion owning the context.
     */
    val minionId: MinionId

    /**
     * Identifier of the Scenario being executed.
     */
    val scenarioId: ScenarioId

    /**
     * Identifier of the DirectedAcyclicGraph being executed.
     */
    val directedAcyclicGraphId: DirectedAcyclicGraphId

    /**
     * Step which generated the source.
     */
    val parentStepId: StepId?

    /**
     * Step executing the context (it should be set by the step itself).
     */
    var stepId: StepId

    /**
     * Nature of the executed step, if defined.
     */
    var stepType: String?

    /**
     * Family or plugin the executed step belongs, if defined.
     */
    var stepFamily: String?

    /**
     * Index of the current iteration for the same step and context.
     */
    var stepIterationIndex: Long

    /**
     * Number of successive execution attempts with failures for the same step and context.
     */
    var attemptsAfterFailure: Long

    /**
     * Creation timestamp of the context.
     */
    var creation: Long

    /**
     * When set to true, the context can neither be used for a new iteration nor propagated.
     */
    var isExhausted: Boolean

    /**
     * When set to true, this means that no more data will be provided to the workflow after this context.
     */
    var isCompleted: Boolean

    /**
     * Specifies that this context is the last in the convoy for the relate minion.
     *
     * It is initialized to true, because at the beginning, minion's convoys are made of a single context.
     * More contexts come when reaching an iterative step, a data source...
     */
    var isTail: Boolean

    /**
     * List of the errors, from current and previous steps.
     */
    val errors: List<StepError>

    /**
     * Checks if the input is empty.
     */
    val hasInput: Boolean

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

    fun <T> next(input: OUT, stepId: StepId): StepContext<OUT, T>

    fun duplicate(
        newInput: ReceiveChannel<IN> = Channel(Channel.UNLIMITED),
        newOutput: SendChannel<OUT> = Channel(Channel.UNLIMITED)
    ): StepContext<IN, OUT>

    fun <T> next(stepId: StepId): StepContext<OUT, T>

    /**
     * Converts the context to a map that can be used as tags for logged events.
     */
    fun toEventTags(): Map<String, String>

    /**
     * Converts the context to a map that can be used as tags for meters. The tags should not contain
     * any detail about the minion, but remains at the level of step, scenario and campaign.
     */
    fun toMetersTags(): Tags
}
