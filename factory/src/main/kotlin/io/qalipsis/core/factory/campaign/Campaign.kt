package io.qalipsis.core.factory.campaign

import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.directives.DispatcherChannel

/**
 * Configuration of a scheduled or running campaign.
 *
 * @author Eric Jessé
 */
data class Campaign(
    val campaignKey: CampaignKey,
    val broadcastChannel: DispatcherChannel,
    val feedbackChannel: DispatcherChannel,
    val assignments: Collection<FactoryScenarioAssignment>
)
