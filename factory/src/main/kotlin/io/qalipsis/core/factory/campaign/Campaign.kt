package io.qalipsis.core.factory.campaign

import io.qalipsis.api.campaign.FactoryScenarioAssignment
import io.qalipsis.api.context.CampaignName
import io.qalipsis.core.directives.DispatcherChannel

/**
 * Configuration of a scheduled or running campaign.
 *
 * @author Eric Jessé
 */
data class Campaign(
    val campaignName: CampaignName,
    val broadcastChannel: DispatcherChannel,
    val feedbackChannel: DispatcherChannel,
    val assignments: Collection<FactoryScenarioAssignment>
)
