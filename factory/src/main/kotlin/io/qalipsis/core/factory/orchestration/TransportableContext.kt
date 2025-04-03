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

package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.factory.context.StepContextImpl
import io.qalipsis.core.serialization.SerializedRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Polymorphic
abstract class TransportableContext : Directive() {
    abstract val campaignKey: CampaignKey
    abstract val scenarioName: ScenarioName
    abstract val minionId: MinionId
    override var channel: DispatcherChannel = ""
}

@Serializable
@SerialName("co")
data class TransportableCompletionContext(
    override val campaignKey: CampaignKey,
    override val scenarioName: ScenarioName,
    override val minionId: MinionId,
    val minionStart: Long,
    val lastExecutedStepName: StepName,
    val errors: List<StepError>,
) : TransportableContext() {

    constructor(ctx: CompletionContext) : this(
        ctx.campaignKey,
        ctx.scenarioName,
        ctx.minionId,
        ctx.minionStart,
        ctx.lastExecutedStepName,
        ctx.errors.map { StepError(it.message, it.stepName) }
    )

    fun toContext(): CompletionContext {
        return DefaultCompletionContext(
            campaignKey,
            scenarioName,
            minionId,
            minionStart,
            lastExecutedStepName,
            errors.map { io.qalipsis.api.context.StepError(it.message, it.stepName) }
        )
    }
}

@Serializable
@SerialName("st")
data class TransportableStepContext(
    val input: SerializedRecord?,
    override val campaignKey: CampaignKey,
    override val scenarioName: ScenarioName,
    override val minionId: MinionId,
    val startedAt: Long,
    val previousStepName: StepName,
    val stepName: StepName,
    var stepType: String?,
    var stepFamily: String?,
    val stepIterationIndex: Long,
    var isExhausted: Boolean,
    var isTail: Boolean,
    val errors: List<StepError>
) : TransportableContext() {

    constructor(ctx: StepContext<*, *>, input: SerializedRecord?) : this(
        input = input,
        campaignKey = ctx.campaignKey,
        scenarioName = ctx.scenarioName,
        minionId = ctx.minionId,
        startedAt = ctx.startedAt,
        previousStepName = ctx.previousStepName!!,
        stepName = ctx.stepName,
        stepType = ctx.stepType,
        stepFamily = ctx.stepFamily,
        stepIterationIndex = ctx.stepIterationIndex,
        isExhausted = ctx.isExhausted,
        isTail = ctx.isTail,
        ctx.errors.map { StepError(it.message, it.stepName) }
    )

    suspend fun toContext(input: Any?, generatedOutput: Boolean): StepContext<*, *> {
        return StepContextImpl<Any?, Any?>(
            input = if (generatedOutput) {
                Channel<Any?>(1).also { it.send(input) }
            } else Channel(Channel.RENDEZVOUS),
            campaignKey = campaignKey,
            scenarioName = scenarioName,
            minionId = minionId,
            previousStepName = previousStepName,
            stepName = stepName,
            stepType = stepType,
            stepFamily = stepFamily,
            stepIterationIndex = stepIterationIndex,
            isExhausted = isExhausted,
            isTail = isTail,
            startedAt = startedAt
        ).also { ctx ->
            this.errors.forEach {
                ctx.addError(io.qalipsis.api.context.StepError(it.message, it.stepName))
            }
        }
    }

    @Suppress("kotlin:S3776")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransportableStepContext

        if (input != null) {
            if (other.input == null) return false
            if (input != other.input) return false
        } else if (other.input != null) return false
        if (campaignKey != other.campaignKey) return false
        if (scenarioName != other.scenarioName) return false
        if (minionId != other.minionId) return false
        if (startedAt != other.startedAt) return false
        if (previousStepName != other.previousStepName) return false
        if (stepName != other.stepName) return false
        if (stepType != other.stepType) return false
        if (stepFamily != other.stepFamily) return false
        if (stepIterationIndex != other.stepIterationIndex) return false
        if (isExhausted != other.isExhausted) return false
        if (isTail != other.isTail) return false
        if (errors != other.errors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = input?.hashCode() ?: 0
        result = 31 * result + campaignKey.hashCode()
        result = 31 * result + scenarioName.hashCode()
        result = 31 * result + minionId.hashCode()
        result = 31 * result + startedAt.hashCode()
        result = 31 * result + previousStepName.hashCode()
        result = 31 * result + stepName.hashCode()
        result = 31 * result + (stepType?.hashCode() ?: 0)
        result = 31 * result + (stepFamily?.hashCode() ?: 0)
        result = 31 * result + stepIterationIndex.hashCode()
        result = 31 * result + isExhausted.hashCode()
        result = 31 * result + isTail.hashCode()
        result = 31 * result + errors.hashCode()
        return result
    }


}

@Serializable
data class StepError(val message: String, var stepName: String)