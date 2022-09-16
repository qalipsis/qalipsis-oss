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
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.communication.AbstractHeadChannel
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.slf4j.event.Level

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD]),
    Requires(
        property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY,
        value = ExecutionEnvironments.REDIS
    )
)
@ExperimentalLettuceCoroutinesApi
internal class RedisHeadChannel(
    private val directiveRegistry: DirectiveRegistry,
    @Named(RedisPubSubConfiguration.PUBLISHER_BEAN_NAME) private val publisherCommands: RedisPubSubReactiveCommands<String, ByteArray>,
    @Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME) private val subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>,
    private val serializer: DistributionSerializer
) : AbstractHeadChannel(), HeadChannel {

    @LogInput(Level.DEBUG)
    override fun subscribeHandshakeRequest(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet() - subscribedHandshakeRequestsChannels
        if (relevantChannels.isNotEmpty()) {
            subscribedHandshakeRequestsChannels += relevantChannels
            subscriberCommands.subscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun unsubscribeHandshakeRequest(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet().intersect(subscribedHandshakeRequestsChannels)
        if (relevantChannels.isNotEmpty()) {
            subscribedHandshakeRequestsChannels -= relevantChannels
            subscriberCommands.unsubscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun subscribeFeedback(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet() - subscribedFeedbackChannels
        if (relevantChannels.isNotEmpty()) {
            subscribedFeedbackChannels += relevantChannels
            subscriberCommands.subscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun unsubscribeFeedback(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet().intersect(subscribedFeedbackChannels)
        if (relevantChannels.isNotEmpty()) {
            subscribedFeedbackChannels -= relevantChannels
            subscriberCommands.unsubscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishDirective(directive: Directive) {
        log.debug { "Sending a directive to the channel ${directive.channel}" }
        publisherCommands.publish(
            directive.channel,
            serializer.serialize(directiveRegistry.prepareBeforeSend(directive.channel, directive))
        ).toFuture().asSuspended().get()
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishHandshakeResponse(
        channelName: DispatcherChannel,
        handshakeResponse: HandshakeResponse
    ) {
        publisherCommands.publish(channelName, serializer.serialize(handshakeResponse)).toFuture().asSuspended().get()
    }

    private companion object {

        val log = logger()

    }
}