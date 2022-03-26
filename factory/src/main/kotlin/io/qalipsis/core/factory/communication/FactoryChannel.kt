package io.qalipsis.core.factory.communication

import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.lifetime.FactoryStartupComponent

/**
 * Communication component in charge of consuming incoming messages to the factory and dispatching outgoing ones.
 *
 * @author Eric Jessé
 */
interface FactoryChannel : FactoryStartupComponent {

    val subscribedHandshakeResponseChannels: Collection<DispatcherChannel>

    val subscribedDirectiveChannels: Collection<DispatcherChannel>

    /**
     * Subscribes to the channels [channelNames] to consume the [io.qalipsis.core.handshake.HandshakeResponse]s.
     */
    fun subscribeHandshakeResponse(vararg channelNames: DispatcherChannel)

    /**
     * Closes the subscription to the channels [channelNames] for the [io.qalipsis.core.handshake.HandshakeResponse]s.
     */
    fun unsubscribeHandshakeResponse(vararg channelNames: DispatcherChannel)

    /**
     * Subscribes to the channels [channelNames] to consume the [Directive]s.
     */
    fun subscribeDirective(vararg channelNames: DispatcherChannel)

    /**
     * Closes the subscription to the channels [channelNames] for the [Directive]s.
     */
    fun unsubscribeDirective(vararg channelNames: DispatcherChannel)

    /**
     * Sends a [Directive] to the channel specified in [directive] or broadcast it when the channel is blank.
     */
    suspend fun publishDirective(directive: Directive)

    /**
     * Sends a [Directive] to the channel [channel].
     */
    suspend fun publishDirective(channel: DispatcherChannel, directive: Directive)

    /**
     * Sends a [Feedback] to the heads.
     */
    suspend fun publishFeedback(feedback: Feedback)

    /**
     * Sends a [HandshakeRequest] to the heads.
     */
    suspend fun publishHandshakeRequest(handshakeRequest: HandshakeRequest)

    /**
     * Sends a [Heartbeat] to the heads.
     */
    suspend fun publishHeartbeat(channel: DispatcherChannel, heartbeat: Heartbeat)

}