package io.qalipsis.core.head.campaign

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.Page

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

    /**
     * Returns list of all campaigns. Filter is a comma-separated list of values to apply
     * as wildcard filters on the campaign, user and scenario names. For example,  “foo,bar” and *foo* or *bar* will
     * be searched in both the campaign, user and the scenario names
     */
    suspend fun search(tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int): Page<Campaign>

    /**
     * Sets the user who aborted the campaign.
     */
    suspend fun setAborter(tenant: String, aborter: String, campaignKey: String)
}