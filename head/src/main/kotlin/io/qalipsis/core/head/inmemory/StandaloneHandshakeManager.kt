package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.handshake.HandshakeManager
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
@Replaces(HandshakeManager::class)
internal class StandaloneHandshakeManager(
    headChannel: HeadChannel,
    idGenerator: IdGenerator,
    factoryService: FactoryService,
    headConfiguration: HeadConfiguration
) : HandshakeManager(
    headChannel,
    idGenerator,
    factoryService,
    headConfiguration
) {

    override fun giveNodeIdToFactory(nodeRegistrationId: String): String {
        return STANDALONE_FACTORY_NAME
    }

    companion object {

        const val STANDALONE_FACTORY_NAME = "_embedded_"
    }
}