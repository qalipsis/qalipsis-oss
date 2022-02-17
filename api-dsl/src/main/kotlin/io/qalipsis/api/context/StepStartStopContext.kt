package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags

/**
 * Data class to pass to a step when starting and stopping it.
 *
 * @property campaignId identifier of the test campaign owning the context
 * @property scenarioId identifier of the Scenario being executed
 * @property dagId identifier of the DirectedAcyclicGraph being executed
 * @property stepId identifier of the Step being initialized
 * @property properties contains properties to start the steps
 *
 * @author Eric Jessé
 */
data class StepStartStopContext(
    val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    val dagId: DirectedAcyclicGraphId,
    val stepId: StepId,
    val properties: Map<String, String> = emptyMap()
) : MonitoringTags {

    override fun toEventTags(): Map<String, String> {
        return mutableMapOf(
            "campaign" to campaignId,
            "scenario" to scenarioId,
            "dag" to dagId,
            "step" to stepId,
        )
    }

    override fun toMetersTags(): Tags {
        return Tags.of(
            "campaign", campaignId,
            "scenario", scenarioId,
            "dag", dagId,
            "step", stepId
        )
    }
}