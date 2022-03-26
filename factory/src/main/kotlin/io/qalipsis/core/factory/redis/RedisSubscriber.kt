package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.HandshakeResponseListener
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.lifetime.FactoryStartupComponent
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.FACTORY]),
    Requires(
        property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY,
        value = ExecutionEnvironments.REDIS
    )
)
@ExperimentalLettuceCoroutinesApi
internal class RedisSubscriber(
    private val factoryChannel: RedisFactoryChannel,
    private val directiveListeners: Collection<DirectiveListener<*>>,
    private val handshakeResponseListeners: Collection<HandshakeResponseListener>,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val orchestrationCoroutineScope: CoroutineScope,
    private val directiveRegistry: DirectiveRegistry,
    @Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME) private val subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>,
    private val serializer: DistributionSerializer
) : FactoryStartupComponent, Closeable {

    private val listener = Listener()

    override fun init() {
        subscriberCommands.statefulConnection.addListener(listener)
    }

    private inner class Listener : RedisPubSubListener<String, ByteArray> {

        override fun message(channel: String, message: ByteArray) {
            log.trace { "Received a message from channel $channel" }
            when (channel) {
                in factoryChannel.subscribedDirectiveChannels ->
                    serializer.deserialize<Directive>(message)?.let { dispatch(it) }
                in factoryChannel.subscribedHandshakeResponseChannels ->
                    serializer.deserialize<HandshakeResponse>(message)?.let { dispatch(it) }
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
     * Dispatches the [Directive] to the relevant [DirectiveListener] in isolated coroutines.
     */
    @Suppress("UNCHECKED_CAST")
    private fun dispatch(directive: Directive) {
        orchestrationCoroutineScope.launch {
            log.trace { "Dispatching the directive of type ${directive::class}" }
            val eligibleListeners = directiveListeners.filter { it.accept(directive) }
            if (eligibleListeners.isNotEmpty()) {
                directiveRegistry.prepareAfterReceived(directive)?.let { preparedDirective ->
                    eligibleListeners.forEach { listener ->
                        orchestrationCoroutineScope.launch {
                            log.trace { "Dispatching the directive of type ${preparedDirective::class} to the listener of type ${listener::class}" }
                            (listener as DirectiveListener<Directive>).notify(preparedDirective)
                        }
                    }
                }
            }
        }
    }

    /**
     * Dispatches the [HandshakeResponse] to all the relevant [HandshakeResponseListener] in isolated coroutines.
     */
    private fun dispatch(response: HandshakeResponse) {
        log.trace { "Dispatching the handshake response" }
        handshakeResponseListeners.stream().forEach { listener ->
            orchestrationCoroutineScope.launch { listener.notify(response) }
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