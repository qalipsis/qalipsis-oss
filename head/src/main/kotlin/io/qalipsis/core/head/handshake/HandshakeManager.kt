/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.head.handshake

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.campaign.ChannelNameFactory
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.lifetime.HeadStartupComponent
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Component to handle the handshakes coming from the factories.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.HEAD])
class HandshakeManager(
    private val headChannel: HeadChannel,
    private val factoryService: FactoryService,
    private val headConfiguration: HeadConfiguration,
    private val channelNameFactory: ChannelNameFactory,
    private val idGenerator: IdGenerator
) : HeadStartupComponent, HandshakeRequestListener {

    override fun getStartupOrder() = Int.MIN_VALUE

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(handshakeRequest: HandshakeRequest) {
        val (nodeRegistrationId, unicastChannel) = resolveNodeIdAndUnicastChannel(handshakeRequest)

        val response = HandshakeResponse(
            handshakeNodeId = handshakeRequest.nodeId,
            nodeId = nodeRegistrationId,
            unicastChannel = unicastChannel,
            heartbeatChannel = headConfiguration.heartbeatChannel,
            heartbeatPeriod = headConfiguration.heartbeatDelay
        )
        log.info { "The factory $nodeRegistrationId just started the handshake, persisting its state..." }
        factoryService.register(nodeRegistrationId, handshakeRequest, response)

        log.debug { "Sending handshake response $response to ${handshakeRequest.replyTo}" }
        headChannel.publishHandshakeResponse(handshakeRequest.replyTo, response)
    }

    /**
     * Resolves the ID and unicast channel to assign to the factory, never reusing those of a factory
     * registered in another tenant.
     */
    private suspend fun resolveNodeIdAndUnicastChannel(handshakeRequest: HandshakeRequest): Pair<NodeId, String> {
        val requestedNodeId = handshakeRequest.nodeId
        return when {
            requestedNodeId.isBlank() || requestedNodeId.startsWith("_") ->
                idGenerator.short() to channelNameFactory.getUnicastChannelName(handshakeRequest)

            factoryService.isNodeIdOwnedByAnotherTenant(handshakeRequest.tenant, requestedNodeId) -> {
                val generatedNodeId = idGenerator.short()
                log.warn {
                    "The node ID $requestedNodeId requested by a factory of the tenant ${handshakeRequest.tenant} " +
                            "is already used in another tenant, the ID $generatedNodeId is assigned instead"
                }
                generatedNodeId to channelNameFactory.getUnicastChannelName(handshakeRequest.tenant, generatedNodeId)
            }

            else -> requestedNodeId to channelNameFactory.getUnicastChannelName(handshakeRequest)
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
