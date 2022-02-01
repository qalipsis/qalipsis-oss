package io.qalipsis.core.head.orchestration

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.report.CampaignReport

/**
 * Service in charge of keep track of the campaign behaviors.
 *
 * @author Eric Jessé
 */
interface CampaignReportStateKeeper {

    /**
     * Cleans all the states and messages kept into the registry for the provided campaign.
     */
    suspend fun clear(campaignId: CampaignId)

    /**
     * Notifies the start of a new campaign.
     */
    suspend fun start(campaignId: CampaignId, scenarioId: ScenarioId)

    /**
     * Notifies the completion of a scenario in a campaign, whether successful or not.
     */
    suspend fun complete(campaignId: CampaignId, scenarioId: ScenarioId)

    /**
     * Notifies the completion of the whole campaign, whether successful or not.
     */
    suspend fun complete(campaignId: CampaignId)

    /**
     * Releases the campaign.
     */
    suspend fun abort(campaignId: CampaignId)

    /**
     * Reports the state of all the scenarios executed in a campaign.
     */
    suspend fun report(campaignId: CampaignId): CampaignReport
}
