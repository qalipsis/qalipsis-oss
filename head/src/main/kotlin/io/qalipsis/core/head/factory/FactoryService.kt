package io.qalipsis.core.head.factory

import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.heartbeat.Heartbeat

/**
 * Service to proceed with the factories data and states in the head.
 *
 * @author Eric Jessé
 */
interface FactoryService {

    /**
     * Registers a new factory and updates an existing one.
     */
    suspend fun register(actualNodeid: String, handshakeRequest: HandshakeRequest)

    /**
     * Update the state of a factory considering the received [Heartbeat].
     */
    suspend fun updateHeartbeat(heartbeat: Heartbeat)

    /**
     * Returns all the scenario currently available in the cluster.
     */
    suspend fun getAllScenarios(ids: Collection<String>): Collection<ScenarioSummary>
}