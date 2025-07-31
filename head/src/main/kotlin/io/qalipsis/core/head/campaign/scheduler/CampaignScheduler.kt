package io.qalipsis.core.head.campaign.scheduler

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.model.CampaignConfiguration
import java.time.Instant

/**
 * Service in charge of executing scheduled campaigns.
 *
 * @author Joël Valère
 */
interface CampaignScheduler {

    /**
     * Start a campaign test at the specified instant.
     *
     * @param campaignKey key of the campaign
     * @param instant instant to start the campaign test
     */
    suspend fun schedule(campaignKey: CampaignKey, instant: Instant)

    /**
     * Schedule a campaign test to execute frequently.
     *
     * @param tenant tenant owning the campaign to schedule
     * @param configurer name of the user who configured the campaign
     * @param configuration configuration of the campaign to schedule
     */
    suspend fun schedule(
        tenant: String,
        configurer: String,
        configuration: CampaignConfiguration
    ): RunningCampaign

    /**
     * Updates a scheduled campaign test.
     *
     * @param tenant tenant owning the campaign to schedule
     * @param configurer name of the user who configured the campaign
     * @param campaignKey unique identifier of the campaign to be updated
     * @param configuration configuration of the campaign schedule to be updated
     */
    suspend fun update(
        tenant: String,
        configurer: String,
        campaignKey: CampaignKey,
        configuration: CampaignConfiguration
    ): RunningCampaign

}