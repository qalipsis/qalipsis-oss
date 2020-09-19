package io.evolue.api.orchestration

import io.evolue.api.context.ScenarioId
import io.evolue.api.context.StepId
import io.evolue.api.retry.NoRetryPolicy
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.Step
import io.evolue.core.factories.orchestration.rampup.RampUpStrategy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A [Scenario] represents a full chain of [DirectedAcyclicGraph]s containing all the steps to perform.
 */
class Scenario(
    /**
     * ID of the Scenario.
     */
    val id: ScenarioId,

    /**
     * List of all the [DirectedAcyclicGraph]s of the scenario.
     */
    val dags: MutableList<DirectedAcyclicGraph> = mutableListOf(),

    /**
     * [RampUpStrategy] to define how fast the minions should be started when executing the scenario.
     */
    val rampUpStrategy: RampUpStrategy,

    /**
     * [RetryPolicy] to define on the steps of the scenario when none is explicitly set.
     */
    val defaultRetryPolicy: RetryPolicy = NoRetryPolicy(),

    /**
     * Default minions count to run the scenario when runtime factor is 1.
     */
    val minionsCount: Int = 1
) {

    private val steps = mutableMapOf<StepId, Channel<Step<*, *>>>()

    fun addStep(step: Step<*, *>) {
        steps.computeIfAbsent(step.id) { Channel(1) }.offer(step)
    }

    /**
     * The find step operation is suspended to wait until the step is created.
     */
    suspend fun findStep(stepId: StepId): Step<*, *>? {
        return withTimeoutOrNull(10000) {
            val chan = steps.computeIfAbsent(stepId) { Channel(1) }
            val step = chan.receive()
            chan.offer(step)
            step
        }
    }

}
