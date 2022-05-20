package io.qalipsis.core.head.web.model

import io.qalipsis.api.campaign.CampaignConfiguration

/**
 * Interface of convertor from [CampaignRequest] to [CampaignConfiguration].
 *
 * @author Palina Bril
 */
internal interface CampaignConfigurationConverter {

    /**
     * Converts from [CampaignRequest] to [CampaignConfiguration].
     */
    fun convertCampaignRequestToConfiguration(
        tenant: String,
        campaign: CampaignRequest
    ): CampaignConfiguration
}