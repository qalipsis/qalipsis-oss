package io.qalipsis.core.head.campaign

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import java.time.Instant

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
        tenant: String,
        configurer: String,
        campaignConfiguration: CampaignConfiguration
    ): RunningCampaign

    suspend fun retrieve(tenant: String, campaignKey: CampaignKey): Campaign

    suspend fun start(tenant: String, campaignKey: CampaignKey, start: Instant, timeout: Instant?)

    suspend fun startScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName, start: Instant)

    suspend fun closeScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Marks a campaign as complete.
     */
    suspend fun close(tenant: String, campaignKey: CampaignKey, result: ExecutionStatus): Campaign

    /**
     * Returns list of all campaigns. Filter is a comma-separated list of values to apply
     * as wildcard filters on the campaign, user and scenario names. For example,  “foo,bar” and *foo* or *bar* will
     * be searched in both the campaign, user and the scenario names
     */
    suspend fun search(tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int): Page<Campaign>

    /**
     * Sets the user who aborted the campaign.
     */
    suspend fun abort(tenant: String, aborter: String, campaignKey: CampaignKey)
}