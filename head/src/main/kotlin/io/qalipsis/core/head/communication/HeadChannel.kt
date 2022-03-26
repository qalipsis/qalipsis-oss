package io.qalipsis.core.factory.communication

import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.lifetime.FactoryStartupComponent

/**
 * Communication component in charge of consuming incoming messages to the head and dispatching outgoing ones.
 *
 * @author Eric Jessé
 */
interface HeadChannel : FactoryStartupComponent {

    val subscribedHandshakeRequestsChannels: Collection<DispatcherChannel>

    val subscribedFeedbackChannels: Collection<DispatcherChannel>

    /**
     * Subscribes to the channels [channelNames] to consume the [io.qalipsis.core.handshake.HandshakeRequest]s.
     */
    fun subscribeHandshakeRequest(vararg channelNames: DispatcherChannel)

    /**
     * Closes the subscription to the channels [channelNames] for the [io.qalipsis.core.handshake.HandshakeRequest]s.
     */
    fun unsubscribeHandshakeRequest(vararg channelNames: DispatcherChannel)

    /**
     * Subscribes to the channels [channelNames] to consume the [io.qalipsis.core.feedbacks.Feedback]s.
     */
    fun subscribeFeedback(vararg channelNames: DispatcherChannel)

    /**
     * Closes the subscription to the channels [channelNames] for the [io.qalipsis.core.feedbacks.Feedback]s.
     */
    fun unsubscribeFeedback(vararg channelNames: DispatcherChannel)

    /**
     * Sends a [Directive] to the channel specified in [directive].
     */
    suspend fun publishDirective(directive: Directive)

    /**
     * Sends a [HandshakeResponse] to a factory via the channel [channelName].
     */
    suspend fun publishHandshakeResponse(channelName: DispatcherChannel, handshakeResponse: HandshakeResponse)

}