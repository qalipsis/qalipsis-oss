package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.CommunicationChannelConfiguration
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import jakarta.inject.Singleton

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
    communicationChannelConfiguration: CommunicationChannelConfiguration,
    factoryChannel: FactoryChannel
) : InitializationContext(factoryConfiguration, communicationChannelConfiguration, factoryChannel) {

    /**
     * There is no need to persist the node for the standalone mode.
     */
    override fun persistNodeIdIfDifferent(actualNodeId: String) = Unit
}