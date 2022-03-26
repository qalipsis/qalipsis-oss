package io.qalipsis.core.head.handshake

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.lifetime.HeadStartupComponent
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Component to handle the handshakes coming from the factories..
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.HEAD])
internal class HandshakeManager(
    private val headChannel: HeadChannel,
    private val idGenerator: IdGenerator,
    private val factoryService: FactoryService,
    private val headConfiguration: HeadConfiguration
) : HeadStartupComponent, HandshakeRequestListener {

    override fun getStartupOrder() = Int.MIN_VALUE

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(handshakeRequest: HandshakeRequest) {
        val nodeRegistrationId = handshakeRequest.nodeId
        val actualNodeId = giveNodeIdToFactory(nodeRegistrationId)

        val response = HandshakeResponse(
            handshakeNodeId = handshakeRequest.nodeId,
            nodeId = actualNodeId,
            unicastChannel = headConfiguration.unicastChannelPrefix + actualNodeId,
            heartbeatChannel = headConfiguration.heartbeatChannel,
            heartbeatPeriod = headConfiguration.heartbeatDuration
        )
        log.info { "The factory $actualNodeId just started the handshake, persisting its state..." }
        factoryService.register(actualNodeId, handshakeRequest, response)

        log.debug { "Sending handshake response $response to ${handshakeRequest.replyTo}" }
        headChannel.publishHandshakeResponse(handshakeRequest.replyTo, response)
    }

    protected fun giveNodeIdToFactory(nodeRegistrationId: String) =
        if (nodeRegistrationId.isBlank() || nodeRegistrationId.startsWith("_")) {
            idGenerator.short()
        } else {
            nodeRegistrationId
        }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
