package io.qalipsis.core.head.campaign.scheduler

import io.qalipsis.api.context.CampaignKey
import java.time.Instant

/**
 * Service in charge of executing scheduled campaigns.
 *
 * @author Joël Valère
 */
internal interface CampaignScheduler {

    /**
     * Start a campaign test at the specified instant.
     *
     * @param campaignKey key of the campaign
     * @param instant instant to start the campaign test
     */
    suspend fun schedule(campaignKey: CampaignKey, instant: Instant)
}