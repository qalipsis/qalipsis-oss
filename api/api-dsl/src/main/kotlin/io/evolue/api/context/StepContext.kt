package io.evolue.api.context

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.time.Instant

/**
 * Data class containing all the required information to execute a step on a minion.
 *
 * @author Eric Jessé
 */
data class StepContext<IN : Any?, OUT : Any?>(

    /**
     * Channel providing the source.
     */
    val input: ReceiveChannel<IN> = Channel(1),

    /**
     * Channel to push the result.
     */
    val output: SendChannel<OUT> = Channel(1),

    /**
     * List of the generated errors so far.
     */
    val errors: MutableList<StepError> = mutableListOf(),

    /**
     * Identifier of the Minion owning the context.
     */
    val minionId: MinionId,

    /**
     * Identifier of the Scenario being executed.
     */
    val scenarioId: ScenarioId,

    /**
     * Identifier of the DirectedAcyclicGraph being executed.
     */
    val directedAcyclicGraphId: DirectedAcyclicGraphId,

    /**
     * Step which generated the source.
     */
    val parentStepId: StepId? = null,

    /**
     * Step executing the context (it should be set by the step itself).
     */
    var stepId: StepId,

    /**
     * Index of the current iteration for the same step and context.
     */
    var stepIterationIndex: Long = 0,

    /**
     * Number of successive execution attempts with failures for the same step and context.
     */
    var attemptsAfterFailure: Long = 0,

    /**
     * Creation timestamp of the context.
     */
    var creation: Long = System.currentTimeMillis(),

    /**
     * When set to true, the context can neither be used for a new iteration nor propagated.
     */
    var exhausted: Boolean = false,

    /**
     * When set to true, this means that no more data will be provided to the workflow.
     */
    var completed: Boolean = false
) {

    /**
     * Converts the context to a map that can be used as tags for logged events.
     */
    fun toEventTagsMap(): Map<String, String> {
        val result = mutableMapOf(
            "minion" to minionId,
            "scenario" to scenarioId,
            "dag" to directedAcyclicGraphId,
            "step" to stepId,
            "iteration" to stepIterationIndex.toString(),
            "attempts-after-failure" to attemptsAfterFailure.toString(),
            "context-creation" to Instant.ofEpochMilli(creation).toString(),
            "exhausted" to exhausted.toString(),
            "completed" to completed.toString()
        )

        parentStepId?.let { result["parent-step"] = it }

        return result
    }
}

