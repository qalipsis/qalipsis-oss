package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags

/**
 * Data class to pass to a step when starting and stopping it.
 *
 * @property campaignName identifier of the test campaign owning the context
 * @property scenarioName identifier of the Scenario being executed
 * @property dagId identifier of the DirectedAcyclicGraph being executed
 * @property stepName identifier of the Step being initialized
 * @property properties contains properties to start the steps
 *
 * @author Eric Jessé
 */
data class StepStartStopContext(
    val campaignName: CampaignName,
    val scenarioName: ScenarioName,
    val dagId: DirectedAcyclicGraphName,
    val stepName: StepName,
    val properties: Map<String, String> = emptyMap()
) : MonitoringTags {

    override fun toEventTags(): Map<String, String> {
        return mutableMapOf(
            "campaign" to campaignName,
            "scenario" to scenarioName,
            "dag" to dagId,
            "step" to stepName,
        )
    }

    override fun toMetersTags(): Tags {
        return Tags.of(
            "campaign", campaignName,
            "scenario", scenarioName,
            "dag", dagId,
            "step", stepName
        )
    }
}