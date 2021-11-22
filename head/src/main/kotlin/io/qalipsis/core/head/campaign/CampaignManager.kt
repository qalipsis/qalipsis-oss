package io.qalipsis.core.head.campaign


/**
 * Component to manage a new campaign.
 *
 * @author Eric Jessé
 *
 */
internal interface CampaignManager {

    /**
     * Starts a new campaign with the provided configuration.
     */
    suspend fun start(
        campaignConfiguration: CampaignConfiguration,
        onCriticalFailure: (String) -> Unit = { _ -> }
    )
}
