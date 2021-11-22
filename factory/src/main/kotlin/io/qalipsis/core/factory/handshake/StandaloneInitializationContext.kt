package io.qalipsis.core.factory.handshake

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.DirectiveConsumer
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.handshake.HandshakeFactoryChannel
import io.qalipsis.core.heartbeat.HeartbeatEmitter
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Version of [InitializationContext] that does not persist the assigned ID.
 *
 * @author Eric Jessé
 */
@Singleton
@Primary
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class StandaloneInitializationContext(
    factoryConfiguration: FactoryConfiguration,
    feedbackFactoryChannel: FeedbackFactoryChannel,
    directiveConsumer: DirectiveConsumer,
    handshakeFactoryChannel: HandshakeFactoryChannel,
    heartbeatEmitter: HeartbeatEmitter,
    @Named(Executors.GLOBAL_EXECUTOR_NAME) coroutineDispatcher: CoroutineDispatcher,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) coroutineScope: CoroutineScope,
) : InitializationContext(
    factoryConfiguration,
    feedbackFactoryChannel,
    directiveConsumer,
    handshakeFactoryChannel,
    heartbeatEmitter,
    coroutineDispatcher,
    coroutineScope
) {

    /**
     * There is no need to persist the node for the standalone mode.
     */
    override fun persistNodeIdIfDifferent(actualNodeId: String) = Unit
}