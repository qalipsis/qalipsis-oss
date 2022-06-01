package io.qalipsis.core.head.campaign

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.model.Campaign

/**
 * Service in charge of maintaining the campaigns.
 *
 * @author Eric Jessé
 */
internal interface CampaignService {

    /**
     * Saves a new campaign for the first time.
     * @param configurer consider the user's name  who configure the campaign
     */
    suspend fun create(
        configurer: String,
        campaignDisplayName: String,
        campaignConfiguration: CampaignConfiguration
    ): Campaign

    /**
     * Marks a campaign as complete.
     */
    suspend fun close(tenant: String, campaignKey: String, result: ExecutionStatus): Campaign

}