package io.qalipsis.core.head.campaign

import io.micronaut.data.model.Page
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
     * Returns list of all campaigns. Filter is a comma-separated list of values to apply
     * as wildcard filters on the campaign, user and scenario names. For example,  “foo,bar” and *foo* or *bar* will
     * be searched in both the campaign, user and the scenario names
     */
    suspend fun getAllCampaigns(tenant: String, filter: String?, sort: String?, page: Int, size: Int): Page<CampaignEntity>
}