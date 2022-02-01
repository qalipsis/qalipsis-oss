package io.qalipsis.core.head.factory

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.model.NodeId
import io.qalipsis.core.heartbeat.Heartbeat

/**
 * Service to proceed with the factories data and states in the head.
 *
 * @author Eric Jessé
 */
internal interface FactoryService {

    /**
     * Registers a new factory and updates an existing one.
     */
    suspend fun register(actualNodeId: String, handshakeRequest: HandshakeRequest)

    /**
     * Update the state of a factory considering the received [Heartbeat].
     */
    suspend fun updateHeartbeat(heartbeat: Heartbeat)

    /**
     * Returns all factories supporting the scenarios with the given identifiers.
     */
    suspend fun getAvailableFactoriesForScenarios(scenarioIds: Collection<String>): Collection<Factory>

    /**
     * Marks all the specified factories as busy and lock them for future use.
     */
    suspend fun lockFactories(campaignConfiguration: CampaignConfiguration, factories: Collection<NodeId>)

    /**
     * Releases the factories to make them available for further campaigns.
     */
    suspend fun releaseFactories(campaignConfiguration: CampaignConfiguration, factories: Collection<NodeId>)

    /**
     * Returns all the scenario currently available in the cluster with the given identifiers.
     */
    suspend fun getActiveScenarios(ids: Collection<ScenarioId>): Collection<ScenarioSummary>
}