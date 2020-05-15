package io.evolue.core.factory.orchestration

import io.evolue.api.context.ScenarioId
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy

/**
 * // TODO
 */
internal class Scenario(
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
    val rampUpStrategy: RampUpStrategy
)