package io.qalipsis.core.head.report

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.report.CampaignReport

/**
 * Service to retrieve the report of a campaign from a database.
 *
 * @author Francisca Eze
 */
interface CampaignReportProvider {
    /**
     * Retrieves the CampaignReport matching the specified [campaignKey].
     *
     * @param tenant the reference of the tenant owning the data.
     * @param campaignKey the ID of the campaign to retrieve
     */
    suspend fun retrieveCampaignReport(tenant: String, campaignKey: CampaignKey): CampaignReport
}