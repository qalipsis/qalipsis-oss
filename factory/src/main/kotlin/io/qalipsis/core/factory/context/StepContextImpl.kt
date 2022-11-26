/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.context

import io.micrometer.core.instrument.Tags
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.sync.Latch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Container for all the required information to execute a step on a minion and monitor the execution.
 *
 * @property input Channel providing the value to use for the execution
 * @property output Channel to push the result
 * @property internalErrors List of the generated errors so far
 *
 * @author Eric Jessé
 */
internal class StepContextImpl<IN, OUT>(
    private val input: ReceiveChannel<IN> = Channel(1),
    val output: SendChannel<StepContext.StepOutputRecord<OUT>> = Channel(Channel.UNLIMITED),
    private val internalErrors: MutableCollection<StepError> = LinkedHashSet(),
    override val campaignKey: CampaignKey = "",
    override val minionId: MinionId,
    override val scenarioName: ScenarioName,
    override val previousStepName: StepName? = null,
    override var stepName: StepName,
    override var stepType: String? = null,
    override var stepFamily: String? = null,
    override var stepIterationIndex: Long = 0,
    override var isExhausted: Boolean = false,
    override var isTail: Boolean = true,
    override val startedAt: Long = System.currentTimeMillis()
) : StepContext<IN, OUT> {

    /**
     * Latch belonging to the step context, used to synchronize it.
     */
    private var latch: Latch? = null
    private var immutableEventTags: Map<String, String>? = null
    private var immutableMetersTags: Tags? = null
    override var generatedOutput: Boolean = false

    override val errors: List<StepError>
        get() = internalErrors.toList()

    override val hasInput: Boolean
        get() = !input.isEmpty

    override val equivalentCompletionContext: CompletionContext
        get() = DefaultCompletionContext(
            campaignKey = campaignKey,
            scenarioName = scenarioName,
            minionId = minionId,
            minionStart = startedAt,
            lastExecutedStepName = stepName,
            errors = errors
        )

    override fun addError(error: StepError) {
        if (error.stepName.isEmpty()) {
            error.stepName = stepName
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

    override fun <T : Any?> next(input: OUT, stepName: StepName): StepContext<OUT, T> {
        return (this.next<T>(stepName) as StepContextImpl<OUT, T>).also {
            (it.input as Channel<OUT>).trySend(input)
        }
    }

    override fun <T : Any?> next(stepName: StepName): StepContext<OUT, T> {
        return StepContextImpl(
            internalErrors = LinkedHashSet(internalErrors),
            campaignKey = campaignKey,
            minionId = minionId,
            scenarioName = scenarioName,
            previousStepName = this.stepName,
            stepName = stepName,
            isExhausted = isExhausted,
            isTail = isTail,
            startedAt = startedAt
        )
    }

    override fun duplicate(
        inputChannel: ReceiveChannel<IN>?,
        outputChannel: SendChannel<StepContext.StepOutputRecord<OUT>>?,
        stepIterationIndex: Long
    ): StepContext<IN, OUT> {
        val sourceInput = this.input
        return StepContextImpl(
            input = inputChannel ?: sourceInput,
            output = outputChannel ?: this.output,
            campaignKey = campaignKey,
            minionId = minionId,
            scenarioName = scenarioName,
            previousStepName = this.previousStepName,
            stepName = stepName,
            stepIterationIndex = stepIterationIndex,
            isExhausted = isExhausted,
            isTail = isTail,
            startedAt = startedAt
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

    override fun toEventTags(): Map<String, String> {
        if (immutableEventTags == null) {
            val tags = mutableMapOf(
                "campaign" to campaignKey,
                "minion" to minionId,
                "scenario" to scenarioName,
                "iteration" to "$stepIterationIndex"
            )
            previousStepName?.let { tags["previous-step"] = it }
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
                "campaign", campaignKey,
                "scenario", scenarioName,
                "step", stepName
            )
            previousStepName?.let { tags = tags.and("previous-step", it) }
            immutableMetersTags = tags
        }
        return immutableMetersTags!!
    }

    override suspend fun close() {
        input.cancel()
        output.close()
        super.close()
    }

    override fun toString(): String {
        return "StepContextImpl(campaignKey='$campaignKey', minionId='$minionId', scenarioName='$scenarioName', parentStepName=$previousStepName, stepName='$stepName', isTail='$isTail', iteration='$stepIterationIndex')"
    }

}

