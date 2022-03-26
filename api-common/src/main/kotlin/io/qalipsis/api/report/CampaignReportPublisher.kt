package io.qalipsis.api.report

import io.qalipsis.api.campaign.CampaignConfiguration

/**
 * Service in charge of publishing the campaigns reports.
 *
 * @author Palina Bril
 */
interface CampaignReportPublisher {

    /**
     * Publishes a report for a completed campaign.
     *
     * @param campaign configuration of the completed campaign
     * @param report report of the completed campaign
     */
    suspend fun publish(campaign: CampaignConfiguration, report: CampaignReport)

}