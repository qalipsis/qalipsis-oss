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

package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.beans.BeanIntrospection
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.factory.FactoryHealth
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Implementation of the [FactoryService] to keep the data in-memory only.
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.TRANSIENT])
)
internal class InMemoryFactoryService(
    private val scenarioSummaryRepository: ScenarioSummaryRepository
) : FactoryService {

    private val factoriesByNodeId = ConcurrentHashMap<NodeId, LockableFactory>()

    private val factoriesByScenarios = ConcurrentHashMap<ScenarioName, MutableCollection<NodeId>>()

    @LogInput
    override suspend fun register(
        actualNodeId: NodeId,
        handshakeRequest: HandshakeRequest,
        handshakeResponse: HandshakeResponse
    ) {
        factoriesByNodeId[actualNodeId] = LockableFactory(
            nodeId = actualNodeId,
            registrationTimestamp = Instant.now(),
            unicastChannel = handshakeResponse.unicastChannel,
            version = Instant.now(),
            tags = handshakeRequest.tags,
            activeScenarios = handshakeRequest.scenarios.map { it.name },
            zone = handshakeRequest.zone
        )
        handshakeRequest.scenarios.forEach { scenario ->
            factoriesByScenarios.computeIfAbsent(scenario.name) { concurrentSet() } += actualNodeId
        }
        scenarioSummaryRepository.saveAll(handshakeRequest.scenarios)
    }

    @LogInput
    override suspend fun notify(heartbeat: Heartbeat) {
        if (heartbeat.state == Heartbeat.State.OFFLINE) {
            factoriesByNodeId.remove(heartbeat.nodeId)?.activeScenarios?.forEach { scenarioName ->
                factoriesByScenarios.computeIfPresent(scenarioName) { _, factories ->
                    // Delete the factory from the set of factories supporting the scenario.
                    factories.remove(heartbeat.nodeId)
                    // If the set of factories is now empty, it is removed from the map.
                    factories.takeIf(Collection<String>::isNotEmpty)
                }
            }
        } else {
            factoriesByNodeId[heartbeat.nodeId]?.healthState?.set(heartbeat)
        }
    }

    @LogInputAndOutput
    override suspend fun getAvailableFactoriesForScenarios(
        tenant: String,
        scenarioNames: Collection<ScenarioName>
    ): Collection<Factory> {
        return scenarioNames.flatMap { scenarioName ->
            factoriesByScenarios[scenarioName]
                ?.mapNotNull { nodeId -> factoriesByNodeId[nodeId]?.takeIf(::isAvailableAndHealthy) } ?: emptyList()
        }.distinctBy { it.nodeId }
    }

    /**
     * Verifies whether the factory is not already locked and sent a healthy heartbeat in the last [HEALTH_QUERY_INTERVAL].
     */
    private fun isAvailableAndHealthy(factory: LockableFactory): Boolean {
        return !factory.locked.get()
                && factory.healthState.get()
            .run { (state == Heartbeat.State.IDLE || state == Heartbeat.State.REGISTERED) && timestamp >= Instant.now() - HEALTH_QUERY_INTERVAL }
    }

    @LogInput
    override suspend fun lockFactories(runningCampaign: RunningCampaign, factories: Collection<NodeId>) {
        factories.forEach { nodeId -> factoriesByNodeId[nodeId]?.locked?.set(true) }
    }

    @LogInput
    override suspend fun releaseFactories(runningCampaign: RunningCampaign, factories: Collection<NodeId>) {
        factories.forEach { nodeId -> factoriesByNodeId[nodeId]?.locked?.set(false) }
    }

    @LogInputAndOutput
    override suspend fun getActiveScenarios(
        tenant: String,
        ids: Collection<ScenarioName>
    ): Collection<ScenarioSummary> {
        return scenarioSummaryRepository.getAll(ids)
    }

    @LogInputAndOutput
    override suspend fun getAllActiveScenarios(tenant: String, sort: String?): Collection<ScenarioSummary> {
        sort?.let {
            val sortProperty = BeanIntrospection.getIntrospection(ScenarioSummary::class.java).beanProperties
                .firstOrNull {
                    it.name == sort.trim().split(":").get(0)
                }
            val sortOrder = sort.trim().split(":").last()
            return if ("desc" == sortOrder) {
                scenarioSummaryRepository.getAll().sortedBy { sortProperty?.get(it) as Comparable<Any> }.reversed()
            } else {
                scenarioSummaryRepository.getAll().sortedBy { sortProperty?.get(it) as Comparable<Any> }
            }
        }
        return scenarioSummaryRepository.getAll()
    }

    @LogInputAndOutput
    override suspend fun getFactoriesHealth(
        tenant: String,
        factories: Collection<NodeId>
    ): Collection<FactoryHealth> {
        return factories.map {
            val factory = factoriesByNodeId[it]
            FactoryHealth(
                factory?.nodeId as String,
                factory.healthState.get().timestamp,
                factory.healthState.get().state
            )
        }
    }

    /**
     * Internal representation of a factory for the in-memory storage, that can be locked.
     */
    private class LockableFactory(
        nodeId: NodeId,
        registrationTimestamp: Instant,
        unicastChannel: String,
        version: Instant,
        tags: Map<String, String> = emptyMap(),
        activeScenarios: Collection<String> = emptySet(),
        val locked: AtomicBoolean = AtomicBoolean(false),
        val healthState: AtomicReference<Heartbeat> = AtomicReference(
            Heartbeat(
                nodeId = nodeId,
                tenant = Defaults.TENANT,
                timestamp = Instant.now(),
                state = Heartbeat.State.REGISTERED
            )
        ),
        zone: String?
    ) : Factory(nodeId, registrationTimestamp, unicastChannel, version, tags, activeScenarios, zone)

    private companion object {

        val HEALTH_QUERY_INTERVAL = Duration.ofMinutes(2)
    }
}