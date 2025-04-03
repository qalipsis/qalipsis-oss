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

package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.HEAD])
internal class DefaultChannelNameFactory(
    private val idGenerator: IdGenerator
) : ChannelNameFactory {

    override suspend fun getBroadcastChannelName(campaign: RunningCampaign): String {
        return BROADCAST_CONTEXTS_CHANNEL
    }

    override suspend fun getFeedbackChannelName(campaign: RunningCampaign): String {
        return FEEDBACK_CONTEXTS_CHANNEL
    }

    override suspend fun getUnicastChannelName(handshakeRequest: HandshakeRequest): String {
        return if (handshakeRequest.nodeId.isBlank() || handshakeRequest.nodeId.startsWith("_")) {
            idGenerator.short()
        } else {
            handshakeRequest.nodeId
        }
    }

    companion object {

        const val BROADCAST_CONTEXTS_CHANNEL = "directives-broadcast"

        const val FEEDBACK_CONTEXTS_CHANNEL = "feedbacks"
    }
}