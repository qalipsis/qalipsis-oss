package io.qalipsis.core.head.model.converter

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignRequest

/**
 * Interface of convertor from [CampaignRequest] to [CampaignConfiguration].
 *
 * @author Palina Bril
 */
internal interface CampaignConverter {

    /**
     * Converts from [CampaignRequest] to [CampaignConfiguration].
     */
    suspend fun convertRequest(tenant: String, campaign: CampaignRequest): CampaignConfiguration

    suspend fun convertToModel(campaignEntity: CampaignEntity): Campaign

    /**
     * Converts from [CampaignReport] to [io.qalipsis.core.head.model.CampaignReport].
     */
    suspend fun convertReport(campaignReport: CampaignReport): io.qalipsis.core.head.model.CampaignReport
}