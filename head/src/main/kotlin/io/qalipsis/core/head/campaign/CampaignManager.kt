package io.qalipsis.core.head.campaign

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.model.CampaignConfiguration


/**
 * Component to manage the execution of campaigns.
 *
 * @author Eric Jessé
 */
internal interface CampaignManager {

    /**
     * Starts a new campaign with the provided configuration.
     *
     * @param configurer username of the user who configured the campaign
     * @param configuration configuration of the campaign to execute
     */
    suspend fun start(tenant: String, configurer: String, configuration: CampaignConfiguration): RunningCampaign

    /**
     * Aborts a campaign with the provided name.
     * @param aborter is a username of the user aborting the campaign
     * @param tenant define in which tenant the abortion of the campaign should be done
     * @param campaignKey is a name of the campaign to abort
     * @param hard force the campaign to fail when set to true, defaults to false
     */
    suspend fun abort(aborter: String, tenant: String, campaignKey: String, hard: Boolean)
}
