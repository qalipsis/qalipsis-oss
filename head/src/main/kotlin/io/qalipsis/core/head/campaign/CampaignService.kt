package io.qalipsis.core.head.campaign

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.report.ExecutionStatus

/**
 * Service in charge of maintaining the campaigns.
 *
 * @author Eric Jessé
 */
interface CampaignService {

    /**
     * Saves a new campaign for the first time.
     */
    suspend fun save(configurer: String, campaignConfiguration: CampaignConfiguration)

    /**
     * Marks a campaign as complete.
     */
    suspend fun close(campaignName: CampaignName, result: ExecutionStatus)

}