/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.test.steps

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
    override val campaignKey: CampaignKey = "",
    override val minionId: MinionId,
    override val scenarioName: ScenarioName,
    override val previousStepName: StepName? = null,
    override var stepName: StepName,
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

    private var immutableMetersTags: Map<String, String>? = null

    override val startedAt: Long = System.currentTimeMillis()

    override val errors: List<StepError>
        get() = internalErrors.toList()

    override val hasInput: Boolean
        get() = !input.isEmpty

    override var generatedOutput: Boolean = false

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

    override fun <T : Any?> next(input: OUT, stepName: StepName): TestStepContext<OUT, T> {
        return this.next<T>(stepName).also {
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
            campaignKey = campaignKey,
            minionId = minionId,
            scenarioName = scenarioName,
            previousStepName = this.stepName,
            stepName = stepName,
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

    override fun <T : Any?> next(stepName: StepName): TestStepContext<OUT, T> {
        return TestStepContext(
            input = Channel(1),
            internalErrors = LinkedHashSet(internalErrors),
            campaignKey = campaignKey,
            minionId = minionId,
            scenarioName = scenarioName,
            previousStepName = this.stepName,
            stepName = stepName,
            isExhausted = isExhausted,
            isTail = isTail
        )
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

    override fun toMetersTags(): Map<String, String> {
        if (immutableMetersTags == null) {
            val tags = mutableMapOf(
                "campaign" to campaignKey,
                "scenario" to scenarioName,
                "step" to stepName
            )
            previousStepName?.let { tags["parent-step"] = it }
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

