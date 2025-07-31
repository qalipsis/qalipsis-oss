/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
class CommunicationChannelConfiguration : CampaignLifeCycleAware {

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