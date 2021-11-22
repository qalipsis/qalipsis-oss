package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Singleton

/**
 * Implementation of the [FactoryService] to keep the data in-memory only.
 */
@Singleton
@Requires(env = [ExecutionEnvironments.VOLATILE])
internal class InMemoryFactoryService(
    private val scenarioSummaryRepository: ScenarioSummaryRepository
) : FactoryService {

    override suspend fun register(actualNodeid: String, handshakeRequest: HandshakeRequest) {
        scenarioSummaryRepository.saveAll(handshakeRequest.scenarios)
    }

    override suspend fun updateHeartbeat(heartbeat: Heartbeat) {
        // Nothing to do.
    }

    override suspend fun getAllScenarios(ids: Collection<String>): Collection<ScenarioSummary> {
        return scenarioSummaryRepository.getAll(ids)
    }
}