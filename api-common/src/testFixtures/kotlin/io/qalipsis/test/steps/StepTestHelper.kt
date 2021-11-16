package io.qalipsis.test.steps

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking

/**
 *
 * @author Eric Jessé
 */
object StepTestHelper {

    @SuppressWarnings("kotlin:S107")
    fun <IN : Any?, OUT : Any?> createStepContext(
        input: IN? = null, outputChannel: SendChannel<OUT?> = Channel(100),
        errors: MutableList<StepError> = mutableListOf(),
        minionId: MinionId = "my-minion",
        scenarioId: ScenarioId = "",
        directedAcyclicGraphId: DirectedAcyclicGraphId = "",
        parentStepId: StepId = "my-parent-step",
        stepId: StepId = "my-step", stepIterationIndex: Long = 0,
        attemptsAfterFailure: Long = 0, isExhausted: Boolean = false,
        completed: Boolean = false
    ): TestStepContext<IN, OUT> {
        val inputChannel = Channel<IN>(1)
        runBlocking {
            input?.let {
                inputChannel.send(it)
            }
        }
        return TestStepContext(
            inputChannel,
            outputChannel,
            errors,
            "",
            minionId,
            scenarioId,
            directedAcyclicGraphId,
            parentStepId,
            stepId,
            "",
            "",
            stepIterationIndex,
            attemptsAfterFailure,
            System.currentTimeMillis(),
            isExhausted,
            completed,
            isTail = true
        )
    }

}
