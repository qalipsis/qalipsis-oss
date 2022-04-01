package io.qalipsis.core.head.orchestration

import io.qalipsis.api.context.CampaignName
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
    suspend fun clear(campaignName: CampaignName)

    /**
     * Notifies the start of a new campaign.
     */
    suspend fun start(campaignName: CampaignName, scenarioName: ScenarioName)

    /**
     * Notifies the completion of a scenario in a campaign, whether successful or not.
     */
    suspend fun complete(campaignName: CampaignName, scenarioName: ScenarioName)

    /**
     * Notifies the completion of the whole campaign, whether successful or not.
     */
    suspend fun complete(campaignName: CampaignName)

    /**
     * Releases the campaign.
     */
    suspend fun abort(campaignName: CampaignName)

    /**
     * Reports the state of all the scenarios executed in a campaign.
     */
    suspend fun report(campaignName: CampaignName): CampaignReport
}
