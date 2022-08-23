package io.qalipsis.core.factory.init

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.CommunicationChannelConfiguration
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.handshake.HandshakeResponse
import jakarta.inject.Singleton


@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY])
internal class ClusterInitializationContext(
    factoryConfiguration: FactoryConfiguration,
    communicationChannelConfiguration: CommunicationChannelConfiguration,
    factoryChannel: FactoryChannel,
    private val handshakeBlocker: HandshakeBlocker,
) : InitializationContext(
    factoryConfiguration,
    communicationChannelConfiguration,
    factoryChannel
) {

    @LogInput
    override suspend fun notify(response: HandshakeResponse) {
        super.notify(response)
        handshakeBlocker.notifySuccessfulRegistration()
    }
}