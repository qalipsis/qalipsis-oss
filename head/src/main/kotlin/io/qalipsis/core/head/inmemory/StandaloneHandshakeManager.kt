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

package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.campaign.ChannelNameFactory
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.handshake.HandshakeManager
import jakarta.inject.Singleton
import org.slf4j.event.Level

@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
@Replaces(HandshakeManager::class)
internal class StandaloneHandshakeManager(
    private val headChannel: HeadChannel,
    private val factoryService: FactoryService,
    private val headConfiguration: HeadConfiguration,
    private val channelNameFactory: ChannelNameFactory,
    idGenerator: IdGenerator
) : HandshakeManager(
    headChannel,
    factoryService,
    headConfiguration,
    channelNameFactory,
    idGenerator
) {

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(handshakeRequest: HandshakeRequest) {
        val actualNodeId = channelNameFactory.getUnicastChannelName(handshakeRequest)
        val response = HandshakeResponse(
            handshakeNodeId = handshakeRequest.nodeId,
            nodeId = actualNodeId,
            unicastChannel = headConfiguration.unicastChannelPrefix + actualNodeId,
            heartbeatChannel = headConfiguration.heartbeatChannel,
            heartbeatPeriod = headConfiguration.heartbeatDelay
        )
        log.info { "The factory $actualNodeId just started the handshake, persisting its state..." }
        factoryService.register(actualNodeId, handshakeRequest, response)

        log.debug { "Sending handshake response $response to ${handshakeRequest.replyTo}" }
        headChannel.publishHandshakeResponse(handshakeRequest.replyTo, response)
    }

    companion object {

        private val log = logger()
    }
}