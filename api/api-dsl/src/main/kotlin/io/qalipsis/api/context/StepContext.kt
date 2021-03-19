package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.time.Duration
import java.time.Instant

/**
 * Data class containing all the required information to execute a step on a minion.
 *
 * @author Eric Jessé
 */
data class StepContext<IN, OUT>(

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
     * Identifier of the test campaign owning the context.
     */
    val campaignId: CampaignId = "",

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
    var isExhausted: Boolean = false,

    /**
     * When set to true, this means that no more data will be provided to the workflow after this context.
     */
    var isCompleted: Boolean = true,

    /**
     * Specifies that this context is the last in the convoy for the relate minion.
     *
     * It is initialized to true, because at the beginning, minion's convoys are made of a single context.
     * More contexts come when reaching an iterative step, a data source...
     */
    var isTail: Boolean = true
) {

    /**
     * Latch belonging to the step context, used to synchronize it.
     */
    private var latch: Latch? = null

    private var immutableEventTags: Map<String, String>? = null

    private var immutableMetersTags: Tags? = null

    /**
     * Metrics of a previous single step execution for later use (assertion, aggregation...).
     */
    private val inheritedMetrics = mutableMapOf<String, Metric>()

    /**
     * Metrics of a single step execution for later use (assertion, aggregation...).
     */
    private var metrics = mutableMapOf<String, Metric>()

    /**
     * Locks the internal latch of the context.
     */
    suspend fun lock() {
        latch = latch ?: Latch()
        latch!!.lock()
    }

    /**
     * Waits for the internal latch of the context to be released.
     */
    suspend fun await() {
        latch?.await()
    }

    /**
     * Releases the internal latch of the context.
     */
    suspend fun release() {
        latch?.release()
    }

    fun recordTimer(name: String, duration: Duration) {
        metrics[name] = Timer(name, duration)
    }

    fun recordCounter(name: String, count: Double) {
        metrics[name] = Counter(name, count)
    }

    /**
     * Return a metric, either inherited from the previous step or created in the current one.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Metric> get(name: String): T? {
        return (metrics[name] ?: inheritedMetrics[name]) as T?
    }

    fun <T : Any?> next(input: OUT, stepId: StepId): StepContext<OUT, T> {
        return this.next<T>(stepId).also {
            (it.input as Channel<OUT>).offer(input)
            it.inheritedMetrics.putAll(it.metrics)
        }
    }


    fun duplicate(newInput: ReceiveChannel<IN> = Channel(1),
                  newOutput: SendChannel<OUT> = Channel(1)): StepContext<IN, OUT> {
        return StepContext(
            input = newInput,
            output = newOutput,
            campaignId = campaignId,
            minionId = minionId,
            scenarioId = scenarioId,
            directedAcyclicGraphId = directedAcyclicGraphId,
            parentStepId = this.stepId,
            stepId = stepId,
            isExhausted = isExhausted,
            isCompleted = isCompleted,
            isTail = isTail,
            creation = creation
        ).also {
            it.metrics = metrics
            it.errors.addAll(this.errors)
            if (!input.isEmpty) {
                // The input value should be in both input channels.
                val inputValue = input.poll()!!
                (newInput as Channel<IN>).offer(inputValue)
                (input as Channel<IN>).offer(inputValue)
            }
        }
    }

    fun <T : Any?> next(stepId: StepId): StepContext<OUT, T> {
        return StepContext<OUT, T>(
            input = Channel(1),
            errors = errors,
            campaignId = campaignId,
            minionId = minionId,
            scenarioId = scenarioId,
            directedAcyclicGraphId = directedAcyclicGraphId,
            parentStepId = this.stepId,
            stepId = stepId,
            isExhausted = isExhausted,
            isCompleted = isCompleted,
            isTail = isTail,
            creation = creation
        ).also {
            it.inheritedMetrics.putAll(it.metrics)
        }
    }

    /**
     * Converts the context to a map that can be used as tags for logged events.
     */
    fun toEventTags(): Map<String, String> {
        if (immutableEventTags == null) {
            val tags = mutableMapOf(
                "campaign" to campaignId,
                "minion" to minionId,
                "scenario" to scenarioId,
                "dag" to directedAcyclicGraphId,
                "step" to stepId,
                "context-creation" to Instant.ofEpochMilli(creation).toString()
            )
            parentStepId?.let { tags["parent-step"] = it }
            immutableEventTags = tags
        }
        return immutableEventTags!!.plus(mutableMapOf(
            "iteration" to stepIterationIndex.toString(),
            "attempts-after-failure" to attemptsAfterFailure.toString(),
            "isExhausted" to isExhausted.toString(),
            "isTail" to isTail.toString(),
            "isCompleted" to isCompleted.toString()
        ))
    }

    /**
     * Converts the context to a map that can be used as tags for meters.
     */
    fun toMetersTags(): Tags {
        if (immutableMetersTags == null) {
            var tags = Tags.of(
                "campaign", campaignId,
                "minion", minionId,
                "scenario", scenarioId,
                "dag", directedAcyclicGraphId,
                "step", stepId
            )
            parentStepId?.let { tags = tags.and("parent-step", it) }
            immutableMetersTags = tags
        }
        return immutableMetersTags!!
    }

    interface Metric {
        val name: String
    }

    data class Timer(
        override val name: String,
        val duration: Duration
    ) : Metric

    data class Counter(
        override val name: String,
        val count: Double
    ) : Metric

    companion object {

        @JvmStatic
        private val log = logger()

    }
}

