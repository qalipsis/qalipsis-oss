package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeHeadChannel
import io.qalipsis.core.head.campaign.CampaignAutoStarter
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.handshake.HandshakeManager
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import java.util.Optional


@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class StandaloneHandshakeManager(
    environment: Environment,
    handshakeHeadChannel: HandshakeHeadChannel,
    idGenerator: IdGenerator,
    campaignAutoStarter: Optional<CampaignAutoStarter>,
    factoryService: FactoryService,
    headConfiguration: HeadConfiguration,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) executionCoroutineScope: CoroutineScope
) : HandshakeManager(
    environment,
    handshakeHeadChannel,
    idGenerator,
    campaignAutoStarter,
    factoryService,
    headConfiguration,
    executionCoroutineScope
) {

    override fun giveNodeIdToFactory(nodeRegistrationId: String): String {
        return InMemoryFactoryService.STANDALONE_FACTORY_NAME
    }
}