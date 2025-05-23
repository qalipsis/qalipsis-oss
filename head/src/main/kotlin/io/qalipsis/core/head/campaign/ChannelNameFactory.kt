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

import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.handshake.HandshakeRequest

interface ChannelNameFactory {

    @LogInput
    suspend fun getBroadcastChannelName(campaign: RunningCampaign): String

    @LogInput
    suspend fun getFeedbackChannelName(campaign: RunningCampaign): String

    suspend fun getUnicastChannelName(handshakeRequest: HandshakeRequest): String {
        return getUnicastChannelName(handshakeRequest.tenant, handshakeRequest.nodeId)
    }

    @LogInputAndOutput
    suspend fun getUnicastChannelName(tenant: String, nodeId: String): String

}