package io.qalipsis.core.factory.campaign

import io.micronaut.core.order.Ordered

/**
 * Interface for services to enable on specific stages of a campaign.
 *
 * @author Eric Jessé
 */
interface CampaignLifeCycleAware : Ordered {

    suspend fun init(campaign: Campaign) = Unit

    suspend fun close(campaign: Campaign) = Unit

}