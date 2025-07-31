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

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.sync.Latch
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Dummy step to transport the tail of a minion when there is no data to transport.
 */
data class TailStepContext(
    override val campaignKey: CampaignKey = "",
    override val minionId: MinionId,
    override val scenarioName: ScenarioName,
    override val previousStepName: StepName? = null,
    override var stepName: StepName,
    override var stepType: String? = null,
    override var stepFamily: String? = null,
    override var stepIterationIndex: Long = 0,
    override var isExhausted: Boolean = false,
    override val startedAt: Long = System.currentTimeMillis()
) : StepContext<Unit, Any> {

    constructor(context: StepContext<*, *>) : this(
        campaignKey = context.campaignKey,
        minionId = context.minionId,
        scenarioName = context.scenarioName,
        previousStepName = context.stepName,
        stepName = "",
        stepIterationIndex = context.stepIterationIndex,
        isExhausted = context.isExhausted,
        startedAt = context.startedAt
    )

    /**
     * Latch belonging to the step context, used to synchronize it.
     */
    private var latch: Latch? = null

    private var immutableEventTags: Map<String, String>? = null

    private var immutableMetersTags: Map<String, String>? = null

    override val equivalentCompletionContext: CompletionContext
        get() = DefaultCompletionContext(
            campaignKey = campaignKey,
            scenarioName = scenarioName,
            minionId = minionId,
            minionStart = startedAt,
            lastExecutedStepName = stepName,
            errors = errors
        )

    override val errors: List<StepError> = emptyList()

    override val generatedOutput: Boolean = false

    override val hasInput: Boolean = false

    override var isTail: Boolean = true

    override fun addError(error: StepError) = Unit

    /**
     * Receives a record from the previous step.
     */
    override suspend fun receive(): Unit = Unit

    /**
     * Send a record to the next steps.
     */
    override suspend fun send(element: Any) = Unit

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

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> next(input: Any, stepName: StepName): StepContext<Any, T> = copy(
        previousStepName = this.stepName,
        stepName = stepName
    ) as StepContext<Any, T>

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> next(stepName: StepName): StepContext<Any, T> = copy(
        previousStepName = this.stepName,
        stepName = stepName
    ) as StepContext<Any, T>

    override fun duplicate(
        inputChannel: ReceiveChannel<Unit>?,
        outputChannel: SendChannel<StepContext.StepOutputRecord<Any>>?,
        stepIterationIndex: Long
    ): StepContext<Unit, Any> = this

    override fun toEventTags(): Map<String, String> {
        if (immutableEventTags == null) {
            val tags = mutableMapOf(
                "campaign" to campaignKey,
                "minion" to minionId,
                "scenario" to scenarioName,
                "step" to stepName,
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
            previousStepName?.let { tags["previous-step"] = it }
            immutableMetersTags = tags
        }
        return immutableMetersTags!!
    }

    override suspend fun close() {
        super.close()
    }

    override fun toString(): String {
        return "TailStepContext(campaignKey='$campaignKey', minionId='$minionId', scenarioName='$scenarioName', parentStepName=$previousStepName, stepName='$stepName')"
    }

}

