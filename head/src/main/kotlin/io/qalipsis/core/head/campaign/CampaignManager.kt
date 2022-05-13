package io.qalipsis.core.head.campaign

import io.qalipsis.api.campaign.CampaignConfiguration


/**
 * Component to manage the execution of campaigns.
 *
 * @author Eric Jessé
 */
internal interface CampaignManager {

    /**
     * Starts a new campaign with the provided configuration.
     */
    suspend fun start(configurer: String, campaign: CampaignConfiguration)
}
