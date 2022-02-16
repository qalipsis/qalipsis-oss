package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.core.factory.context.StepContextImpl
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Polymorphic
abstract class TransportableContext {
    abstract val campaignId: CampaignId
    abstract val scenarioId: ScenarioId
    abstract val minionId: MinionId
}

@Serializable
@SerialName("co")
data class TransportableCompletionContext(
    override val campaignId: CampaignId,
    override val scenarioId: ScenarioId,
    override val minionId: MinionId,
    val lastExecutedStepId: StepId,
    val errors: List<StepError>
) : TransportableContext() {

    constructor(ctx: CompletionContext) : this(
        ctx.campaignId,
        ctx.scenarioId,
        ctx.minionId,
        ctx.lastExecutedStepId,
        ctx.errors.map { StepError(it.message, it.stepId) }
    )

    fun toContext(): CompletionContext {
        return DefaultCompletionContext(
            campaignId,
            scenarioId,
            minionId,
            lastExecutedStepId,
            errors.map { io.qalipsis.api.context.StepError(it.message, it.stepId) }
        )
    }
}

@Serializable
@SerialName("st")
data class TransportableStepContext(
    val input: ByteArray?,
    override val campaignId: CampaignId,
    override val scenarioId: ScenarioId,
    override val minionId: MinionId,
    val previousStepId: StepId,
    val stepId: StepId,
    var stepType: String?,
    var stepFamily: String?,
    val stepIterationIndex: Long,
    var isExhausted: Boolean,
    var isTail: Boolean,
    val errors: List<StepError>
) : TransportableContext() {

    constructor(ctx: StepContext<*, *>, input: ByteArray?) : this(
        input = input,
        campaignId = ctx.campaignId,
        scenarioId = ctx.scenarioId,
        minionId = ctx.minionId,
        previousStepId = ctx.previousStepId!!,
        stepId = ctx.stepId,
        stepType = ctx.stepType,
        stepFamily = ctx.stepFamily,
        stepIterationIndex = ctx.stepIterationIndex,
        isExhausted = ctx.isExhausted,
        isTail = ctx.isTail,
        ctx.errors.map { StepError(it.message, it.stepId) }
    )

    suspend fun toContext(input: Any?, generatedOutput: Boolean): StepContext<*, *> {
        return StepContextImpl<Any?, Any?>(
            input = if (generatedOutput) {
                Channel<Any?>(1).also { it.send(input) }
            } else Channel(Channel.RENDEZVOUS),
            campaignId = campaignId,
            scenarioId = scenarioId,
            minionId = minionId,
            previousStepId = previousStepId,
            stepId = stepId,
            stepType = stepType,
            stepFamily = stepFamily,
            stepIterationIndex = stepIterationIndex,
            isExhausted = isExhausted,
            isTail = isTail
        ).also { ctx ->
            this.errors.forEach {
                ctx.addError(io.qalipsis.api.context.StepError(it.message, it.stepId))
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransportableStepContext

        if (input != null) {
            if (other.input == null) return false
            if (!input.contentEquals(other.input)) return false
        } else if (other.input != null) return false
        if (campaignId != other.campaignId) return false
        if (scenarioId != other.scenarioId) return false
        if (minionId != other.minionId) return false
        if (previousStepId != other.previousStepId) return false
        if (stepId != other.stepId) return false
        if (stepType != other.stepType) return false
        if (stepFamily != other.stepFamily) return false
        if (stepIterationIndex != other.stepIterationIndex) return false
        if (isExhausted != other.isExhausted) return false
        if (isTail != other.isTail) return false
        if (errors != other.errors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = input?.contentHashCode() ?: 0
        result = 31 * result + campaignId.hashCode()
        result = 31 * result + scenarioId.hashCode()
        result = 31 * result + minionId.hashCode()
        result = 31 * result + previousStepId.hashCode()
        result = 31 * result + stepId.hashCode()
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
data class StepError(val message: String, var stepId: String)