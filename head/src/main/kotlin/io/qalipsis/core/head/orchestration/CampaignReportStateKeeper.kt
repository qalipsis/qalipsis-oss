package io.qalipsis.core.head.orchestration

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.CampaignReport

/**
 * Service in charge of keep track of the campaign behaviors.
 *
 * @author Eric Jessé
 */
internal interface CampaignReportStateKeeper {

    /**
     * Cleans all the states and messages kept into the registry for the provided campaign.
     */
    suspend fun clear(campaignKey: CampaignKey)

    /**
     * Notifies the start of a new campaign.
     */
    suspend fun start(campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Notifies the completion of a scenario in a campaign, whether successful or not.
     */
    suspend fun complete(campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Notifies the completion of the whole campaign, whether successful or not.
     */
    suspend fun complete(campaignKey: CampaignKey)

    /**
     * Releases the campaign.
     */
    suspend fun abort(campaignKey: CampaignKey)

    /**
     * Reports the state of all the scenarios executed in a campaign.
     */
    suspend fun generateReport(campaignKey: CampaignKey): CampaignReport?
}
