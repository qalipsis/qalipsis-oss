package io.evolue.core.factories.orchestration

import io.evolue.api.context.CampaignId
import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.orchestration.Scenario

/**
 * <p>
 * The ScenariosKeeper is composed of a registry to keep the full description of all the scenarios supported by
 * the factory as well as an analyzer in charge of decomposing the scenarios when it receives a directive
 * for it from the head.
 * </p>
 *
 * <p>
 * The decomposition of the scenario (requested from the head as a reference to a directive) is then shared to
 * all the factories via messaging and kept in each registry.
 * </p>
 *
 * @author Eric Jessé
 */
internal interface ScenariosKeeper {

    fun hasScenario(scenarioId: ScenarioId): Boolean

    fun getScenario(scenarioId: ScenarioId): Scenario?

    fun hasDag(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): Boolean

    fun getDag(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): DirectedAcyclicGraph?

    fun startScenario(campaignId: CampaignId, scenarioId: ScenarioId)

    fun stopScenario(campaignId: CampaignId, scenarioId: ScenarioId)
}
