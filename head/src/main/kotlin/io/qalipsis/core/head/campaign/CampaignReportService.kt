package io.qalipsis.core.head.campaign

import io.qalipsis.api.report.CampaignReport

/**
 * Service in charge of persitance the campaigns reports.
 *
 * @author Palina Bril
 */
interface CampaignReportService {

    /**
     * Saves a new campaign report for the first time.
     */
    suspend fun save(campaignReport: CampaignReport)

}