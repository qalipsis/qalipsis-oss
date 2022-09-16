package io.qalipsis.core.head.model.converter

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration

/**
 * Interface of convertor from [CampaignConfiguration] to [RunningCampaign].
 *
 * @author Palina Bril
 */
internal interface CampaignConverter {

    /**
     * Converts from [CampaignConfiguration] to [RunningCampaign].
     */
    suspend fun convertConfiguration(tenant: String, campaign: CampaignConfiguration): RunningCampaign

    suspend fun convertToModel(campaignEntity: CampaignEntity): Campaign

}