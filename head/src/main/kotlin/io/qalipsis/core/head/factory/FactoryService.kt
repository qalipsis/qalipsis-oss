package io.qalipsis.core.head.factory

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.communication.HeartbeatListener
import io.qalipsis.core.head.model.Factory

/**
 * Service to proceed with the factories data and states in the head.
 *
 * @author Eric Jessé
 */
internal interface FactoryService : HeartbeatListener {

    /**
     * Registers a new factory and updates an existing one.
     */
    suspend fun register(actualNodeId: NodeId, handshakeRequest: HandshakeRequest, handshakeResponse: HandshakeResponse)

    /**
     * Returns all factories supporting the scenarios with the given identifiers.
     */
    suspend fun getAvailableFactoriesForScenarios(tenant: String, scenarioNames: Collection<ScenarioName>): Collection<Factory>

    /**
     * Marks all the specified factories as busy and lock them for future use.
     */
    suspend fun lockFactories(runningCampaign: RunningCampaign, factories: Collection<NodeId>)

    /**
     * Releases the factories to make them available for further campaigns.
     */
    suspend fun releaseFactories(runningCampaign: RunningCampaign, factories: Collection<NodeId>)

    /**
     * Returns all the scenario currently available in the cluster with the given identifiers.
     */
    suspend fun getActiveScenarios(tenant: String, ids: Collection<ScenarioName>): Collection<ScenarioSummary>

    /**
     * Lists all the active scenarios in [tenant], sorted by [sort].
     */
    suspend fun getAllActiveScenarios(tenant: String, sort: String?): Collection<ScenarioSummary>
}