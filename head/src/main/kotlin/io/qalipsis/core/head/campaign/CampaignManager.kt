package io.qalipsis.core.head.campaign


/**
 * Component to manage the execution of campaigns.
 *
 * @author Eric Jessé
 */
internal interface CampaignManager {

    /**
     * Starts a new campaign with the provided configuration.
     */
    suspend fun start(campaign: CampaignConfiguration)
}
