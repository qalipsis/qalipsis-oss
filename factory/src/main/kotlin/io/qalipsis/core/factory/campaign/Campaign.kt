package io.qalipsis.core.factory.campaign

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.directives.DispatcherChannel

/**
 * Configuration of a scheduled or running campaign.
 *
 * @author Eric Jessé
 */
data class Campaign(
    val campaignId: CampaignId,
    val broadcastChannel: DispatcherChannel,
    val feedbackChannel: DispatcherChannel,
    val assignedDagsByScenario: Map<ScenarioId, Collection<DirectedAcyclicGraphId>>
)
