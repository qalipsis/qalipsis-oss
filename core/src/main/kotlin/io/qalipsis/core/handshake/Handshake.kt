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

package io.qalipsis.core.handshake

import io.qalipsis.api.serialization.DurationKotlinSerializer
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * Notification sent from the factory to the head, when the factory has prepared its scenario and is ready for work.
 *
 * @property nodeId current identifier of the factory sending the feedback
 * @property tags list of tags configured in the factory
 * @property replyTo channel to use for the registration response
 * @property scenarios set of scenarios supported by the factory
 * @property tenant tenant identifier of the factory sending the feedback
 * @property zone a key of a Zone of the factory declared in the head
 *
 * @author Eric Jessé
 */
@Serializable
data class HandshakeRequest(
    val nodeId: String,
    val tags: Map<String, String>,
    val replyTo: String,
    val scenarios: List<RegistrationScenario>,
    val tenant: String = "",
    val zone: String? = null
)

typealias RegistrationScenario = ScenarioSummary
typealias RegistrationDirectedAcyclicGraph = DirectedAcyclicGraphSummary

/**
 * Response to the handshake initialized from a factory.
 *
 * @property handshakeNodeId ID of the node as received from the factory at the beginning of the handshake
 * @property nodeId final ID of the node, as assigned from the head
 * @property unicastChannel name of the channel to listen for the unicast directives dedicated for this node
 * @property heartbeatChannel name of the channel to use to send the heartbeats
 * @property heartbeatPeriod period to emmit the heartbeats
 */
@Serializable
data class HandshakeResponse(
    val handshakeNodeId: String,
    val nodeId: String,
    val unicastChannel: String,
    val heartbeatChannel: String,
    @Serializable(with = DurationKotlinSerializer::class) val heartbeatPeriod: Duration
)