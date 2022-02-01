package io.qalipsis.core.head.campaign

import io.qalipsis.api.context.CampaignId
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
    suspend fun save(campaignConfiguration: CampaignConfiguration)

    /**
     * Marks a campaign as complete.
     */
    suspend fun close(campaignId: CampaignId, result: ExecutionStatus)

}