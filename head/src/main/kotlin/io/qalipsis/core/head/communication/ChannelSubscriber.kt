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

package io.qalipsis.core.head.communication

import io.qalipsis.api.Executors.ORCHESTRATION_EXECUTOR_NAME
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Abstract class to subscribe to channel.
 *
 * @author Joël Valère
 */
abstract class ChannelSubscriber(
    private val serializer: DistributionSerializer,
    private val headConfiguration: HeadConfiguration,
    private val heartbeatListeners: Collection<HeartbeatListener>,
    private val feedbackListeners: Collection<FeedbackListener<*>>,
    private val handshakeRequestListeners: Collection<HandshakeRequestListener>,
    @Named(ORCHESTRATION_EXECUTOR_NAME) private val orchestrationCoroutineScope: CoroutineScope
) {

    val subscribedHandshakeRequestsChannels: MutableSet<DispatcherChannel> = concurrentSet()

    val subscribedFeedbackChannels: MutableSet<DispatcherChannel> = concurrentSet()

    /**
     * Dispatches the [Feedback], [HandshakeRequest] or the [Heartbeat] in isolated coroutines.
     */
    fun deserializeAndDispatch(channel: String, message: ByteArray) {
        when (channel) {
            in subscribedFeedbackChannels -> serializer.deserialize<Feedback>(message)?.let { dispatch(it) }
            in subscribedHandshakeRequestsChannels -> serializer.deserialize<HandshakeRequest>(message)
                ?.let { dispatch(it) }

            headConfiguration.heartbeatChannel -> serializer.deserialize<Heartbeat>(message)?.let { dispatch(it) }
            else -> log.trace { "Channel $channel is not supported" }
        }
    }

    /**
     * Dispatches the [Feedback] to the relevant [FeedbackListener] in isolated coroutines.
     */
    @Suppress("UNCHECKED_CAST")
    private fun dispatch(feedback: Feedback) {
        log.trace { "Dispatching the feedback of type ${feedback::class}" }
        val eligibleListeners = feedbackListeners.filter { it.accept(feedback) }
        if (eligibleListeners.isNotEmpty()) {
            eligibleListeners.forEach { listener ->
                log.trace { "Dispatching the directive of type ${feedback::class} to the listener of type ${listener::class}" }
                orchestrationCoroutineScope.launch {
                    (listener as FeedbackListener<Feedback>).notify(feedback)
                }
            }
        }
    }

    /**
     * Dispatches the [HandshakeRequest] to all the relevant [HandshakeRequestListener] in isolated coroutines.
     */
    private fun dispatch(request: HandshakeRequest) {
        log.trace { "Dispatching the handshake request ${request::class}" }
        handshakeRequestListeners.stream().forEach { listener ->
            orchestrationCoroutineScope.launch { listener.notify(request) }
        }
    }

    /**
     * Dispatches the [heartbeat] to all the relevant [HeartbeatListener] in isolated coroutines.
     */
    private fun dispatch(heartbeat: Heartbeat) {
        log.trace { "Dispatching the heartbeat $heartbeat" }
        heartbeatListeners.stream().forEach { listener ->
            orchestrationCoroutineScope.launch { listener.notify(heartbeat) }
        }
    }

    private companion object {
        val log = logger()
    }
}