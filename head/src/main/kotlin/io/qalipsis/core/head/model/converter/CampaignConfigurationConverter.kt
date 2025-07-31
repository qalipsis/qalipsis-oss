package io.qalipsis.core.head.model.converter

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.model.CampaignConfiguration

/**
 * Interface of converter from [CampaignConfiguration] to [RunningCampaign].
 *
 * @author Svetlana Paliashchuk
 */
interface CampaignConfigurationConverter {

    /**
     * Converts from [CampaignConfiguration] to [RunningCampaign].
     */
    suspend fun convertConfiguration(tenant: String, campaign: CampaignConfiguration): RunningCampaign

}