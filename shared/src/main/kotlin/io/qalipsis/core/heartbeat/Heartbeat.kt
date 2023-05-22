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

package io.qalipsis.core.heartbeat

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.serialization.InstantKotlinSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Signal sent from the factory to the head on a regular basis to distribute the factory inner state.
 *
 * @author Eric Jessé
 */
@Serializable
data class Heartbeat(
    val nodeId: String,
    val tenant: String,
    @Serializable(with = InstantKotlinSerializer::class) val timestamp: Instant,
    val state: State = State.IDLE,
    val campaignKey: CampaignKey? = null
) {

    enum class State {
        REGISTERED, OFFLINE, IDLE, UNHEALTHY
    }

}
