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

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.api.context.NodeId
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.jdbc.SelectorEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.head.jdbc.entity.FactoryTagEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignFactoryRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.FactoryRepository
import io.qalipsis.core.head.jdbc.repository.FactoryStateRepository
import io.qalipsis.core.head.jdbc.repository.FactoryTagRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import java.time.Instant

/**
 * FactoryService implementation for persistence implementation of factory-specific data
 * FactoryRepository, FactoryTagRepository, FactoryStateRepository, ScenarioRepository, DirectedAcyclicGraphRepository, DirectedAcyclicGraphSelectorRepository
 * beans should be injected
 *
 * @author rklymenko
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
)
internal class ClusterFactoryService(
    private val factoryRepository: FactoryRepository,
    private val factoryTagRepository: FactoryTagRepository,
    private val factoryStateRepository: FactoryStateRepository,
    private val scenarioRepository: ScenarioRepository,
    private val campaignRepository: CampaignRepository,
    private val campaignFactoryRepository: CampaignFactoryRepository,
    private val tenantRepository: TenantRepository,
    private val scenarioDetailsUpdater: ScenarioDetailsUpdater,
) : FactoryService {

    /**
     * Creates and updates factory-specific information using handshakeRequest.
     */
    @LogInput
    override suspend fun register(
        actualNodeId: String,
        handshakeRequest: HandshakeRequest,
        handshakeResponse: HandshakeResponse
    ) {
        val tenantId = tenantRepository.findIdByReference(handshakeRequest.tenant)
        val existingFactory = saveFactory(tenantId, actualNodeId, handshakeRequest, handshakeResponse)
        scenarioDetailsUpdater.saveOrUpdateScenarios(
            handshakeRequest.tenant,
            handshakeRequest.scenarios,
            existingFactory
        )
    }

    /**
     * Updates or saves factory and factory_selector entities using handshakeRequest
     */
    private suspend fun saveFactory(
        tenantId: Long,
        actualNodeId: String,
        handshakeRequest: HandshakeRequest,
        handshakeResponse: HandshakeResponse
    ) =
        (updateFactory(actualNodeId, handshakeRequest, handshakeResponse)
            ?: saveNewFactory(tenantId, actualNodeId, handshakeRequest, handshakeResponse))
            .also { factoryEntity ->
                // The state of the factory is changed to REGISTERED.
                val now = Instant.now()
                factoryStateRepository.save(
                    FactoryStateEntity(
                        factoryId = factoryEntity.id,
                        healthTimestamp = now,
                        latency = 0,
                        state = FactoryStateValue.REGISTERED
                    )
                )
            }

    /**
     * Updates existing factory using factory_selector entities from handshakeRequest
     */
    private suspend fun updateFactory(
        actualNodeId: String,
        handshakeRequest: HandshakeRequest,
        handshakeResponse: HandshakeResponse
    ) = factoryRepository.findByNodeIdIn(handshakeRequest.tenant, listOf(actualNodeId))
        .firstOrNull()?.also { entity ->
            // When the entity already exists, its tags are updated.
            mergeTags(factoryTagRepository, handshakeRequest.tags, entity.tags, entity.id)

            if (entity.unicastChannel != handshakeResponse.unicastChannel) {
                if (handshakeRequest.zone.isNullOrEmpty()) {
                    factoryRepository.save(entity.copy(unicastChannel = handshakeResponse.unicastChannel))
                } else {
                    factoryRepository.save(
                        entity.copy(
                            unicastChannel = handshakeResponse.unicastChannel,
                            zone = handshakeRequest.zone
                        )
                    )
                }
            }
        }

    /**
     * Methods merging tags of any kind.
     */
    private suspend fun <T : SelectorEntity<*>> mergeTags(
        repository: CoroutineCrudRepository<T, Long>,
        newTags: Map<String, String>,
        existingTags: Collection<T> = emptyList(),
        entityId: Long
    ) {
        val mutableNewTags = newTags.toMutableMap()
        val (remaining, toDelete) = existingTags.partition { it.key in newTags.keys }
        if (toDelete.isNotEmpty()) {
            repository.deleteAll(toDelete)
        }
        val toUpdate = remaining.filter { it.value != newTags[it.key] }
            .map { it.withValue(mutableNewTags.remove(it.key)!!) }
        if (toUpdate.isNotEmpty()) {
            repository.updateAll(toUpdate as List<T>).collect()
        }
        val toSave =
            mutableNewTags.filter { newSelector -> remaining.none { it.key == newSelector.key } }
                .map { (key, value) -> FactoryTagEntity(entityId, key, value) }
        if (toSave.isNotEmpty()) {
            repository.saveAll(toSave as Iterable<T>).collect()
        }
    }

    /**
     * Persists new factory and factory_selector entities
     */
    private suspend fun saveNewFactory(
        tenantId: Long,
        actualNodeId: String,
        handshakeRequest: HandshakeRequest,
        handshakeResponse: HandshakeResponse
    ): FactoryEntity {
        val factoryEntity = factoryRepository.save(
            FactoryEntity(
                nodeId = actualNodeId,
                registrationTimestamp = Instant.now(),
                registrationNodeId = handshakeRequest.nodeId,
                unicastChannel = handshakeResponse.unicastChannel,
                tenantId = tenantId,
                zone = handshakeRequest.zone
            )
        )
        if (handshakeRequest.tags.isNotEmpty()) {
            factoryTagRepository.saveAll(handshakeRequest.tags.map { (key, value) ->
                FactoryTagEntity(factoryEntity.id, key, value)
            }).count()
        }
        return factoryEntity
    }

    @LogInput
    override suspend fun notify(heartbeat: Heartbeat) {
        val latency = Instant.now().toEpochMilli() - heartbeat.timestamp.toEpochMilli()
        val factoryId = factoryRepository.findIdByNodeIdIn(listOf(heartbeat.nodeId)).first()
        factoryStateRepository.save(
            FactoryStateEntity(
                Instant.now(),
                factoryId,
                heartbeat.timestamp,
                latency,
                FactoryStateValue.valueOf(heartbeat.state.name)
            )
        )
    }

    @LogInputAndOutput
    override suspend fun getAvailableFactoriesForScenarios(
        tenant: String,
        scenarioNames: Collection<String>
    ): Collection<Factory> {
        val scenariosByFactories = scenarioRepository.findActiveByName(tenant, scenarioNames).groupBy { it.factoryId }
        return factoryRepository.getAvailableFactoriesForScenarios(tenant, scenarioNames)
            .map { entity ->
                entity.toModel(scenariosByFactories[entity.id]!!.map { it.name })
            }
    }

    @LogInput
    override suspend fun lockFactories(runningCampaign: RunningCampaign, factories: Collection<NodeId>) {
        if (factories.isNotEmpty()) {
            val factoryIds = factoryRepository.findIdByNodeIdIn(factories)
            if (factoryIds.isNotEmpty()) {
                campaignRepository.findIdByTenantAndKeyAndEndIsNull(runningCampaign.tenant, runningCampaign.key)
                    ?.let { campaignId ->
                        campaignFactoryRepository.saveAll(factoryIds.map { CampaignFactoryEntity(campaignId, it) })
                            .count()
                    }
            }
        }
    }

    @LogInput
    override suspend fun releaseFactories(runningCampaign: RunningCampaign, factories: Collection<NodeId>) {
        if (factories.isNotEmpty()) {
            val factoryIds = factoryRepository.findIdByNodeIdIn(factories)
            if (factoryIds.isNotEmpty()) {
                campaignRepository.findIdByTenantAndKey(runningCampaign.tenant, runningCampaign.key)
                    ?.let { campaignId ->
                        campaignFactoryRepository.discard(campaignId, factoryIds)
                    }
            }
        }
    }

    /**
     * getAllScenarios method finds all active scenarios by given ids
     */
    override suspend fun getActiveScenarios(tenant: String, ids: Collection<String>) =
        scenarioRepository.findActiveByName(tenant, ids).map(ScenarioEntity::toModel)

    override suspend fun getAllActiveScenarios(tenant: String, sort: String?): Collection<ScenarioSummary> {
        return sort?.let {
            val sortProperty = sort.trim().split(":").first()
            val sortOrder = sort.trim().split(":").last()
            if ("desc" == sortOrder) {
                scenarioRepository.findAllActiveByTenantWithSorting(tenant, sortProperty).map(ScenarioEntity::toModel)
                    .reversed()
            } else {
                scenarioRepository.findAllActiveByTenantWithSorting(tenant, sortProperty).map(ScenarioEntity::toModel)
            }
        } ?: scenarioRepository.findAllActiveByTenantWithSorting(tenant, ScenarioEntity::name.name)
            .map(ScenarioEntity::toModel)
    }

    @LogInputAndOutput
    override suspend fun getFactoriesHealth(
        tenant: String,
        factories: Collection<NodeId>
    ): Collection<FactoryHealth> {
        return factoryRepository.getFactoriesHealth(tenant, factories)
    }
}