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

package io.qalipsis.api.context

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Context of a unique execution of a minion on a step.
 * When the same minion repeats the same step in a raw, several contexts should be used.
 *
 * @property campaignKey Identifier of the test campaign owning the context
 * @property minionId Identifier of the Minion owning the context
 * @property scenarioName Identifier of the Scenario being executed
 * @property previousStepName First ancestor step
 * @property stepName Step executing the context
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
    val campaignKey: CampaignKey
    val minionId: MinionId
    val scenarioName: ScenarioName
    val previousStepName: StepName?
    val stepName: StepName
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
     * Creates the [StepContext] for the next step with ID [stepName] and initializes the input of the
     * new context with [input].
     */
    fun <T> next(input: OUT, stepName: StepName): StepContext<OUT, T>

    /**
     * Creates the [StepContext] for the next step with ID [stepName] without any input.
     */
    fun <T> next(stepName: StepName): StepContext<OUT, T>

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
