package io.qalipsis.api.context

/**
 * Data class to pass to a step when starting and stopping it.
 *
 * @property campaignId identifier of the test campaign owning the context
 * @property scenarioId identifier of the Scenario being executed
 * @property dagId identifier of the DirectedAcyclicGraph being executed
 *
 * @author Eric Jessé
 */
data class StepStartStopContext(
        val campaignId: CampaignId,
        val scenarioId: ScenarioId,
        val dagId: DirectedAcyclicGraphId
)