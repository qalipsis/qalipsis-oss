package io.qalipsis.test.steps

import io.micrometer.core.instrument.Tags
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.sync.Latch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Implementation of [StepContext] for test purpose.
 *
 * @property input Channel providing the value to use for the execution
 * @property output Channel to push the result
 * @property internalErrors List of the generated errors so far
 *
 * @author Eric Jessé
 */
class TestStepContext<IN, OUT>(
    val input: ReceiveChannel<IN> = Channel(1),
    val output: SendChannel<StepContext.StepOutputRecord<OUT>> = Channel(1),
    private val internalErrors: MutableCollection<StepError> = LinkedHashSet(),
    override val campaignId: CampaignId = "",
    override val minionId: MinionId,
    override val scenarioId: ScenarioId,
    override val previousStepId: StepId? = null,
    override var stepId: StepId,
    override var stepType: String? = null,
    override var stepFamily: String? = null,
    override var stepIterationIndex: Long = 0,
    override var isExhausted: Boolean = false,
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

    override var generatedOutput: Boolean = false

    override val equivalentCompletionContext: CompletionContext
        get() = DefaultCompletionContext(
            campaignId = campaignId,
            scenarioId = scenarioId,
            minionId = minionId,
            lastExecutedStepId = stepId,
            errors = errors
        )

    override fun addError(error: StepError) {
        internalErrors.add(error)
    }

    /**
     * Receives a record from the previous step.
     */
    override suspend fun receive() = input.receive()

    /**
     * Send a record to the next steps.
     */
    override suspend fun send(element: OUT) {
        output.send(StepContext.StepOutputRecord(element, isTail))
        generatedOutput = true
    }

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

    override fun <T : Any?> next(input: OUT, stepId: StepId): TestStepContext<OUT, T> {
        return this.next<T>(stepId).also {
            (it.input as Channel<OUT>).trySend(input)
        }
    }

    override fun duplicate(
        inputChannel: ReceiveChannel<IN>?,
        outputChannel: SendChannel<StepContext.StepOutputRecord<OUT>>?,
        stepIterationIndex: Long
    ): StepContext<IN, OUT> {
        val sourceInput = this.input
        return TestStepContext(
            input = inputChannel ?: this.input,
            output = outputChannel ?: this.output,
            campaignId = campaignId,
            minionId = minionId,
            scenarioId = scenarioId,
            previousStepId = this.stepId,
            stepId = stepId,
            isExhausted = isExhausted,
            isTail = isTail,
            stepIterationIndex = stepIterationIndex
        ).also {
            it.internalErrors.addAll(this.internalErrors)

            if (input !== sourceInput && !sourceInput.isEmpty) {
                // The input value is copied in the new input, and also remains in the source one.
                val inputValue = input.tryReceive().getOrThrow()
                (inputChannel as Channel<IN>).trySend(inputValue)
                (input as Channel<IN>).trySend(inputValue)
            }
        }
    }

    override fun <T : Any?> next(stepId: StepId): TestStepContext<OUT, T> {
        return TestStepContext(
            input = Channel(1),
            internalErrors = LinkedHashSet(internalErrors),
            campaignId = campaignId,
            minionId = minionId,
            scenarioId = scenarioId,
            previousStepId = this.stepId,
            stepId = stepId,
            isExhausted = isExhausted,
            isTail = isTail
        )
    }

    override fun toEventTags(): Map<String, String> {
        if (immutableEventTags == null) {
            val tags = mutableMapOf(
                "campaign" to campaignId,
                "minion" to minionId,
                "scenario" to scenarioId,
                "iteration" to "$stepIterationIndex"
            )
            previousStepId?.let { tags["previous-step"] = it }
            stepType?.let { tags["step-type"] = it }
            stepFamily?.let { tags["step-family"] = it }
            immutableEventTags = tags
        }
        return immutableEventTags!! + mapOf(
            "isExhausted" to "$isExhausted",
            "isTail" to "$isTail"
        )
    }

    override fun toMetersTags(): Tags {
        if (immutableMetersTags == null) {
            var tags = Tags.of(
                "campaign", campaignId,
                "scenario", scenarioId,
                "step", stepId
            )
            previousStepId?.let { tags = tags.and("parent-step", it) }
            immutableMetersTags = tags
        }
        return immutableMetersTags!!
    }

    /**
     * Receives the [io.qalipsis.api.context.StepContext.StepOutputRecord]s generated by the step.
     */
    suspend fun consumeOutputRecord() = (output as Channel).receive()

    /**
     * Receives the values generated by the step.
     */
    suspend fun consumeOutputValue() = (output as Channel).receive().value

    override suspend fun close() {
        input.cancel()
        output.close()
        super.close()
    }
}

