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

package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.communication.AbstractFactoryChannel
import io.qalipsis.core.factory.communication.SubscriberChannelRegistry
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.slf4j.event.Level

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.FACTORY]),
    Requires(
        property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY,
        value = ExecutionEnvironments.REDIS
    )
)
@ExperimentalLettuceCoroutinesApi
internal class RedisFactoryChannel(
    @Named(RedisPubSubConfiguration.PUBLISHER_BEAN_NAME) private val publisherCommands: RedisPubSubReactiveCommands<String, ByteArray>,
    private val subscriber: BeanProvider<RedisSubscriber>,
    private val subscriberRegistry: SubscriberChannelRegistry,
    @Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME) val subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>
) : AbstractFactoryChannel(subscriberRegistry.directiveRegistry), CampaignLifeCycleAware {

    override suspend fun init(campaign: Campaign) {
        currentDirectiveBroadcastChannel = campaign.broadcastChannel
        currentFeedbackChannel = campaign.feedbackChannel
        subscribeDirective(campaign.broadcastChannel)
    }

    override suspend fun close(campaign: Campaign) {
        unsubscribeDirective(campaign.broadcastChannel)
    }

    @LogInput(Level.DEBUG)
    override fun subscribeHandshakeResponse(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet() - subscriber.get().subscribedHandshakeResponseChannels
        if (relevantChannels.isNotEmpty()) {
            subscriber.get().subscribedHandshakeResponseChannels += relevantChannels
            subscriberCommands.subscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun unsubscribeHandshakeResponse(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet().intersect(subscriber.get().subscribedHandshakeResponseChannels)
        if (relevantChannels.isNotEmpty()) {
            subscriber.get().subscribedHandshakeResponseChannels -= relevantChannels
            subscriberCommands.unsubscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun subscribeDirective(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet() - subscriber.get().subscribedDirectiveChannels
        if (relevantChannels.isNotEmpty()) {
            subscriber.get().subscribedDirectiveChannels += relevantChannels
            subscriberCommands.subscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun unsubscribeDirective(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet().intersect(subscriber.get().subscribedDirectiveChannels)
        if (relevantChannels.isNotEmpty()) {
            subscriber.get().subscribedDirectiveChannels -= relevantChannels
            subscriberCommands.unsubscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishDirective(channel: DispatcherChannel, directive: Directive) {
        log.debug { "Sending a directive to the channel $channel" }
        publisherCommands.publish(channel, subscriberRegistry.serializer.serialize(directive)).toFuture().asSuspended()
            .get()
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishFeedback(feedback: Feedback) {
        if (feedback is CampaignManagementFeedback) {
            feedback.nodeId = subscriberRegistry.factoryConfiguration.nodeId
            feedback.tenant = subscriberRegistry.factoryConfiguration.tenant
        }
        publisherCommands.publish(currentFeedbackChannel, subscriberRegistry.serializer.serialize(feedback)).toFuture()
            .asSuspended().get()
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishHandshakeRequest(handshakeRequest: HandshakeRequest) {
        publisherCommands.publish(
            subscriberRegistry.factoryConfiguration.handshake.requestChannel,
            subscriberRegistry.serializer.serialize(handshakeRequest)
        ).toFuture().asSuspended().get()
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishHeartbeat(channel: DispatcherChannel, heartbeat: Heartbeat) {
        publisherCommands.publish(channel, subscriberRegistry.serializer.serialize(heartbeat)).toFuture().asSuspended()
            .get()
    }

    private companion object {

        val log = logger()

    }
}