package io.qalipsis.core.head.campaign

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity

/**
 * Service in charge of maintaining the campaigns.
 *
 * @author Eric Jessé
 */
interface CampaignService {

    /**
     * Saves a new campaign for the first time.
     * @param configurer consider the user's name  who configure the campaign
     */
    suspend fun create(configurer: String, campaignConfiguration: CampaignConfiguration)

    /**
     * Marks a campaign as complete.
     */
    suspend fun close(campaignName: CampaignName, result: ExecutionStatus)

    /**
     * Returns list of all campaigns
     */
    suspend fun getAllCampaigns(tenant: String, filter: String?, sort: String?): List<CampaignEntity>
}