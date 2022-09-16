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

package io.qalipsis.core.head.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.communication.HeartbeatListener
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.lifetime.HeadStartupComponent
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD]),
    Requires(
        property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY,
        value = ExecutionEnvironments.REDIS
    )
)
@ExperimentalLettuceCoroutinesApi
internal class RedisSubscriber(
    private val factoryChannel: RedisHeadChannel,
    private val headConfiguration: HeadConfiguration,
    private val feedbackListeners: Collection<FeedbackListener<*>>,
    private val handshakeRequestListeners: Collection<HandshakeRequestListener>,
    private val heartbeatListeners: Collection<HeartbeatListener>,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val orchestrationCoroutineScope: CoroutineScope,
    @Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME) private val subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>,
    private val serializer: DistributionSerializer
) : HeadStartupComponent, Closeable {

    private val listener = Listener()

    override fun init() {
        factoryChannel.subscribeHandshakeRequest(headConfiguration.handshakeRequestChannel)
        subscriberCommands.subscribe(headConfiguration.heartbeatChannel).toFuture().get()
        subscriberCommands.statefulConnection.addListener(listener)
    }

    private inner class Listener : RedisPubSubListener<String, ByteArray> {

        override fun message(channel: String, message: ByteArray) {
            log.trace { "Received a message from channel $channel" }
            when (channel) {
                in factoryChannel.subscribedFeedbackChannels ->
                    serializer.deserialize<Feedback>(message)?.let { dispatch(it) }
                in factoryChannel.subscribedHandshakeRequestsChannels ->
                    serializer.deserialize<HandshakeRequest>(message)?.let { dispatch(it) }
                headConfiguration.heartbeatChannel -> serializer.deserialize<Heartbeat>(message)?.let { dispatch(it) }
                else -> log.trace { "Channel $channel is not supported" }
            }
        }

        override fun message(pattern: String, channel: String, message: ByteArray) {
            message(channel, message)
        }

        override fun subscribed(channel: String, count: Long) {
            log.trace { "Subscribed to channel $channel" }
        }

        override fun psubscribed(pattern: String, count: Long) {
            log.trace { "Subscribed to pattern $pattern" }
        }

        override fun unsubscribed(channel: String, count: Long) {
            log.trace { "Unsubscribed from channel $channel" }
        }

        override fun punsubscribed(pattern: String, count: Long) {
            log.trace { "Unsubscribed from pattern $pattern" }
        }
    }

    /**
     * Dispatches the [Feedback] to the relevant [FeedbackListener] in isolated coroutines.
     */
    @Suppress("UNCHECKED_CAST")
    private fun dispatch(feedback: Feedback) {
        log.trace { "Dispatching the directive of type ${feedback::class}" }
        val eligibleListeners = feedbackListeners.filter { it.accept(feedback) }
        if (eligibleListeners.isNotEmpty()) {
            eligibleListeners.forEach { listener ->
                orchestrationCoroutineScope.launch {
                    log.trace { "Dispatching the directive of type ${feedback::class} to the listener of type ${listener::class}" }
                    (listener as FeedbackListener<Feedback>).notify(feedback)
                }
            }
        }
    }

    /**
     * Dispatches the [HandshakeRequest] to all the relevant [HandshakeRequestListener] in isolated coroutines.
     */
    private fun dispatch(request: HandshakeRequest) {
        log.trace { "Dispatching the handshake response $request" }
        handshakeRequestListeners.stream().forEach { listener ->
            orchestrationCoroutineScope.launch { listener.notify(request) }
        }
    }

    /**
     * Dispatches the [HandshakeRequest] to all the relevant [HandshakeRequestListener] in isolated coroutines.
     */
    private fun dispatch(heartbeat: Heartbeat) {
        log.trace { "Dispatching the heartbeat $heartbeat" }
        heartbeatListeners.stream().forEach { listener ->
            orchestrationCoroutineScope.launch { listener.notify(heartbeat) }
        }
    }

    override fun close() {
        subscriberCommands.statefulConnection.removeListener(listener)
        subscriberCommands.quit().subscribe()
    }

    private companion object {
        val log = logger()
    }
}