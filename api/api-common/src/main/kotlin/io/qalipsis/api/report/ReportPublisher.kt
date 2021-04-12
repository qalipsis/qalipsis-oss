package io.qalipsis.api.report

import io.qalipsis.api.context.CampaignId

/**
 * Service in charge of publishing the reports for campaigns in different format.
 *
 * @author Eric Jessé
 */
interface ReportPublisher {

    /**
     * Publish the report for the campaign with [campaignId] as ID.
     */
    fun publish(campaignId: CampaignId)

}
