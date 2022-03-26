package io.qalipsis.core.factory.configuration

import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import jakarta.inject.Singleton

/**
 * Configuration of the communication channels with the head or other factories.
 *
 * @author Eric Jessé
 */
@Singleton
internal class CommunicationChannelConfiguration : CampaignLifeCycleAware {

    /**
     * Unicast channel used by the factory to consume from.
     * Values are set by the response of the handshake with the head.
     */
    var unicastChannel: String = ""

    /**
     * Broadcast channel for the current campaign the factory is involved in.
     */
    var campaignBroadcastChannel: String = ""

    /**
     * Feedback channel for the current campaign the factory is involved in.
     */
    var campaignFeedbackChannel: String = ""

    override suspend fun init(campaign: Campaign) {
        campaignBroadcastChannel = campaign.broadcastChannel
        campaignFeedbackChannel = campaign.feedbackChannel
    }

    override suspend fun close(campaign: Campaign) {
        campaignBroadcastChannel = ""
        campaignFeedbackChannel = ""
    }
}