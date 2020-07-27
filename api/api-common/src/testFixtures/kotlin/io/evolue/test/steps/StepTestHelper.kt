package io.evolue.test.steps

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.MinionId
import io.evolue.api.context.ScenarioId
import io.evolue.api.context.StepContext
import io.evolue.api.context.StepError
import io.evolue.api.context.StepId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking

/**
 *
 * @author Eric Jessé
 */
object StepTestHelper {

    @SuppressWarnings("kotlin:S107")
    fun <IN : Any?, OUT : Any?> createStepContext(input: IN? = null, outputChannel: SendChannel<OUT?> = Channel(100),
        errors: MutableList<StepError> = mutableListOf(),
        minionId: MinionId = "my-minion",
        scenarioId: ScenarioId = "",
        directedAcyclicGraphId: DirectedAcyclicGraphId = "",
        parentStepId: StepId = "my-parent-step",
        stepId: StepId = "my-step", stepIterationIndex: Long = 0,
        attemptsAfterFailure: Long = 0, exhausted: Boolean = false,
        completed: Boolean = false
    ): StepContext<IN, OUT> {
        val inputChannel = Channel<IN>(1)
        runBlocking {
            input?.let {
                inputChannel.send(it)
            }
        }
        return StepContext(inputChannel, outputChannel, errors, "", minionId, scenarioId, directedAcyclicGraphId,
            parentStepId, stepId, stepIterationIndex, attemptsAfterFailure, System.currentTimeMillis(), exhausted,
            completed
        )
    }

}
