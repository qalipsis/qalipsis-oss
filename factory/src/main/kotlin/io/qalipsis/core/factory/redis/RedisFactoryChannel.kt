package io.qalipsis.core.factory.redis

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
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.communication.AbstractFactoryChannel
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.serialization.DistributionSerializer
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
    directiveRegistry: DirectiveRegistry,
    @Named(RedisPubSubConfiguration.PUBLISHER_BEAN_NAME) private val publisherCommands: RedisPubSubReactiveCommands<String, ByteArray>,
    @Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME) private val subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>,
    private val factoryConfiguration: FactoryConfiguration,
    private val serializer: DistributionSerializer
) : AbstractFactoryChannel(directiveRegistry), CampaignLifeCycleAware {

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
        val relevantChannels = channelNames.toSet() - subscribedHandshakeResponseChannels
        if (relevantChannels.isNotEmpty()) {
            subscribedHandshakeResponseChannels += relevantChannels
            subscriberCommands.subscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun unsubscribeHandshakeResponse(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet().intersect(subscribedHandshakeResponseChannels)
        if (relevantChannels.isNotEmpty()) {
            subscribedHandshakeResponseChannels -= relevantChannels
            subscriberCommands.unsubscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun subscribeDirective(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet() - subscribedDirectiveChannels
        if (relevantChannels.isNotEmpty()) {
            subscribedDirectiveChannels += relevantChannels
            subscriberCommands.subscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override fun unsubscribeDirective(vararg channelNames: DispatcherChannel) {
        val relevantChannels = channelNames.toSet().intersect(subscribedDirectiveChannels)
        if (relevantChannels.isNotEmpty()) {
            subscribedDirectiveChannels -= relevantChannels
            subscriberCommands.unsubscribe(*relevantChannels.toTypedArray()).toFuture().get()
        }
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishDirective(channel: DispatcherChannel, directive: Directive) {
        log.debug { "Sending a directive to the channel $channel" }
        publisherCommands.publish(channel, serializer.serialize(directive)).toFuture().asSuspended().get()
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishFeedback(feedback: Feedback) {
        if (feedback is CampaignManagementFeedback) {
            feedback.nodeId = factoryConfiguration.nodeId
        }
        publisherCommands.publish(currentFeedbackChannel, serializer.serialize(feedback)).toFuture().asSuspended().get()
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishHandshakeRequest(handshakeRequest: HandshakeRequest) {
        publisherCommands.publish(
            factoryConfiguration.handshake.requestChannel,
            serializer.serialize(handshakeRequest)
        ).toFuture().asSuspended().get()
    }

    @LogInput(Level.DEBUG)
    override suspend fun publishHeartbeat(channel: DispatcherChannel, heartbeat: Heartbeat) {
        publisherCommands.publish(channel, serializer.serialize(heartbeat)).toFuture().asSuspended().get()
    }

    private companion object {

        val log = logger()

    }
}