package io.qalipsis.test.steps

import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel

/**
 *
 * @author Eric Jessé
 */
object StepTestHelper {

    @SuppressWarnings("kotlin:S107")
    fun <IN, OUT> createStepContext(
        input: IN? = null,
        outputChannel: SendChannel<StepContext.StepOutputRecord<OUT>> = Channel(100),
        errors: MutableList<StepError> = mutableListOf(),
        minionId: MinionId = "my-minion",
        campaignId: ScenarioId = "",
        scenarioId: ScenarioId = "",
        previousStepId: StepId = "my-previous-step",
        stepId: StepId = "my-step",
        stepIterationIndex: Long = 0,
        isExhausted: Boolean = false,
        isTail: Boolean = false
    ): TestStepContext<IN, OUT> {
        val inputChannel = Channel<IN>(1)
        input?.let {
            inputChannel.trySend(it)
        }
        return TestStepContext(
            inputChannel,
            outputChannel,
            errors,
            campaignId,
            minionId,
            scenarioId,
            previousStepId,
            stepId,
            "",
            "",
            stepIterationIndex,
            isExhausted,
            isTail
        )
    }

}
