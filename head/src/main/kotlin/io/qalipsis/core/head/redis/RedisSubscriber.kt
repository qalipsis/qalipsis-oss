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
import io.qalipsis.core.head.communication.ChannelSubscriber
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.communication.HeartbeatListener
import io.qalipsis.core.head.communication.SubscriberChannelRegistry
import io.qalipsis.core.lifetime.HeadStartupComponent
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
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
    val factoryChannel: RedisHeadChannel,
    heartbeatListeners: Collection<HeartbeatListener>,
    feedbackListeners: Collection<FeedbackListener<*>>,
    handshakeRequestListeners: Collection<HandshakeRequestListener>,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val orchestrationCoroutineScope: CoroutineScope,
    @Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME) private val subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>,
    val subscriberRegistry: SubscriberChannelRegistry
) : HeadStartupComponent, Closeable, ChannelSubscriber(
    subscriberRegistry.serializer, subscriberRegistry.headConfiguration,
    heartbeatListeners, feedbackListeners,
    handshakeRequestListeners, orchestrationCoroutineScope
) {

    private val listener = Listener()

    override fun init() {
        factoryChannel.subscribeHandshakeRequest(subscriberRegistry.headConfiguration.handshakeRequestChannel)
        subscriberCommands.subscribe(subscriberRegistry.headConfiguration.heartbeatChannel).toFuture().get()
        subscriberCommands.statefulConnection.addListener(listener)
    }

    private inner class Listener : RedisPubSubListener<String, ByteArray> {

        override fun message(channel: String, message: ByteArray) {
            log.trace { "Received a message from channel $channel" }
            deserializeAndDispatch(channel, message)
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

    override fun close() {
        subscriberCommands.statefulConnection.removeListener(listener)
        subscriberCommands.quit().subscribe()
    }

    private companion object {
        val log = logger()
    }
}