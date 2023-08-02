/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.factory

import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.RunningCampaign
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
    suspend fun getAvailableFactoriesForScenarios(
        tenant: String,
        scenarioNames: Collection<ScenarioName>
    ): Collection<Factory>

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

    /**
     * Checks factories' health in a [tenant].
     *
     * @param tenant the tenant's reference
     * @param factories the collection of factories to check
     */
    suspend fun getFactoriesHealth(tenant: String, factories: Collection<NodeId>): Collection<FactoryHealth>
}