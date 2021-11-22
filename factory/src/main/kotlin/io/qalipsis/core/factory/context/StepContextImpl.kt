package io.qalipsis.core.factory.context

import io.micrometer.core.instrument.Tags
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.sync.Latch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.time.Instant

/**
 * Data class containing all the required information to execute a step on a minion.
 *
 * @author Eric Jessé
 */
internal class StepContextImpl<IN, OUT>(

    /**
     * Channel providing the source.
     */
    val input: ReceiveChannel<IN> = Channel(Channel.UNLIMITED),

    /**
     * Channel to push the result.
     */
    val output: SendChannel<OUT> = Channel(1),

    /**
     * List of the generated errors so far.
     */
    private val internalErrors: MutableCollection<StepError> = LinkedHashSet(),

    override val campaignId: CampaignId = "",

    override val minionId: MinionId,

    override val scenarioId: ScenarioId,

    override val directedAcyclicGraphId: DirectedAcyclicGraphId,

    override val parentStepId: StepId? = null,

    override var stepId: StepId,

    override var stepType: String? = null,

    override var stepFamily: String? = null,

    override var stepIterationIndex: Long = 0,

    override var attemptsAfterFailure: Long = 0,

    override var creation: Long = System.currentTimeMillis(),

    override var isExhausted: Boolean = false,

    override var isCompleted: Boolean = true,

    override var isTail: Boolean = true

) : StepContext<IN, OUT> {

    /**
     * Latch belonging to the step context, used to synchronize it.
     */
    private var latch: Latch? = null

    private var immutableEventTags: Map<String, String>? = null

    private var immutableMetersTags: Tags? = null

    override val errors: List<StepError>
        get() = internalErrors.toList()

    override val hasInput: Boolean
        get() = !input.isEmpty

    override fun addError(error: StepError) {
        if (error.stepId.isEmpty()) {
            error.stepId = stepId
        }
        internalErrors.add(error)
    }

    /**
     * Receives a record from the previous step.
     */
    override suspend fun receive() = input.receive()

    /**
     * Send a record to the next steps.
     */
    override suspend fun send(element: OUT) = output.send(element)

    /**
     * Locks the internal latch of the context.
     */
    override suspend fun lock() {
        latch = latch ?: Latch(name = "step-context-${this}")
        latch!!.lock()
    }

    /**
     * Waits for the internal latch of the context to be released.
     */
    override suspend fun await() {
        latch?.await()
    }

    /**
     * Releases the internal latch of the context.
     */
    override suspend fun release() {
        latch?.release()
    }

    override fun <T : Any?> next(input: OUT, stepId: StepId): StepContext<OUT, T> {
        return (this.next<T>(stepId) as StepContextImpl<OUT, T>).also {
            (it.input as Channel<OUT>).trySend(input)
        }
    }

    override fun duplicate(
        newInput: ReceiveChannel<IN>,
        newOutput: SendChannel<OUT>
    ): StepContext<IN, OUT> {
        return StepContextImpl(
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
            it.internalErrors.addAll(this.internalErrors)
            if (!input.isEmpty) {
                // The input value should be in both input channels.
                val inputValue = input.tryReceive().getOrThrow()
                (newInput as Channel<IN>).trySend(inputValue)
                (input as Channel<IN>).trySend(inputValue)
            }
        }
    }

    override fun <T : Any?> next(stepId: StepId): StepContext<OUT, T> {
        return StepContextImpl(
            internalErrors = LinkedHashSet<StepError>().apply { addAll(internalErrors) },
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
        )
    }

    override fun toEventTags(): Map<String, String> {
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
            stepType?.let { tags["step-type"] = it }
            stepFamily?.let { tags["step-family"] = it }
            immutableEventTags = tags
        }
        return immutableEventTags!!.plus(
            mutableMapOf(
                "iteration" to stepIterationIndex.toString(),
                "attempts-after-failure" to attemptsAfterFailure.toString(),
                "isExhausted" to isExhausted.toString(),
                "isTail" to isTail.toString(),
                "isCompleted" to isCompleted.toString()
            )
        )
    }

    override fun toMetersTags(): Tags {
        if (immutableMetersTags == null) {
            var tags = Tags.of(
                "campaign", campaignId,
                "scenario", scenarioId,
                "dag", directedAcyclicGraphId,
                "step", stepId
            )
            parentStepId?.let { tags = tags.and("parent-step", it) }
            immutableMetersTags = tags
        }
        return immutableMetersTags!!
    }

    override fun toString(): String {
        return "StepContext(campaignId='$campaignId', minionId='$minionId', scenarioId='$scenarioId', directedAcyclicGraphId='$directedAcyclicGraphId', parentStepId=$parentStepId, stepId='$stepId')"
    }


}

