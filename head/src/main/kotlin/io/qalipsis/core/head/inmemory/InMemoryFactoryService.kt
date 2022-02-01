package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.model.NodeId
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Singleton
import java.time.Instant

/**
 * Implementation of the [FactoryService] to keep the data in-memory only.
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class InMemoryFactoryService(
    private val scenarioSummaryRepository: ScenarioSummaryRepository
) : FactoryService {

    override suspend fun register(actualNodeId: String, handshakeRequest: HandshakeRequest) {
        scenarioSummaryRepository.saveAll(handshakeRequest.scenarios)
    }

    override suspend fun updateHeartbeat(heartbeat: Heartbeat) {
        // Nothing to do.
    }

    override suspend fun getAvailableFactoriesForScenarios(scenarioIds: Collection<String>): Collection<Factory> {
        return listOf(FACTORY)
    }

    override suspend fun lockFactories(campaignConfiguration: CampaignConfiguration, factories: Collection<NodeId>) {
        // Nothing to do.
    }

    override suspend fun releaseFactories(campaignConfiguration: CampaignConfiguration, factories: Collection<NodeId>) {
        // Nothing to do.
    }

    override suspend fun getActiveScenarios(ids: Collection<String>): Collection<ScenarioSummary> {
        return scenarioSummaryRepository.getAll(ids)
    }

    private class MutableFactory(
        nodeId: String,
        override var version: Instant,
        override var activeScenarios: Collection<String> = emptySet()
    ) : Factory(nodeId, "", "")

    companion object {

        /**
         * Name of the internal factory when
         */
        const val STANDALONE_FACTORY_NAME = "_embedded_"

        private val FACTORY = MutableFactory(
            nodeId = STANDALONE_FACTORY_NAME,
            version = Instant.now()
        )
    }
}