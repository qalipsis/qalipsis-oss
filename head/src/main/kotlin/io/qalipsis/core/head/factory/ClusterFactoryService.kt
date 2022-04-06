package io.qalipsis.core.head.factory

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.NodeId
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.handshake.RegistrationDirectedAcyclicGraph
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.head.jdbc.SelectorEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphEntity
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphSelectorEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactorySelectorEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignFactoryRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphRepository
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphSelectorRepository
import io.qalipsis.core.head.jdbc.repository.FactoryRepository
import io.qalipsis.core.head.jdbc.repository.FactorySelectorRepository
import io.qalipsis.core.head.jdbc.repository.FactoryStateRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioRepository
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import java.time.Instant

/**
 * FactoryService implementation for persistence implementation of factory-specific data
 * FactoryRepository, FactorySelectorRepository, FactoryStateRepository, ScenarioRepository, DirectedAcyclicGraphRepository, DirectedAcyclicGraphSelectorRepository
 * beans should be injected
 *
 * @author rklymenko
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.VOLATILE])
)
internal class ClusterFactoryService(
    private val factoryRepository: FactoryRepository,
    private val factorySelectorRepository: FactorySelectorRepository,
    private val factoryStateRepository: FactoryStateRepository,
    private val scenarioRepository: ScenarioRepository,
    private val directedAcyclicGraphRepository: DirectedAcyclicGraphRepository,
    private val directedAcyclicGraphSelectorRepository: DirectedAcyclicGraphSelectorRepository,
    private val campaignRepository: CampaignRepository,
    private val campaignFactoryRepository: CampaignFactoryRepository,
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
        val existingFactory = saveFactory(actualNodeId, handshakeRequest, handshakeResponse)
        saveScenariosAndDependencies(handshakeRequest.scenarios, existingFactory)
    }

    /**
     * Updates or saves factory and factory_selector entities using handshakeRequest
     */
    private suspend fun saveFactory(
        actualNodeId: String,
        handshakeRequest: HandshakeRequest,
        handshakeResponse: HandshakeResponse
    ) =
        (updateFactory(actualNodeId, handshakeRequest, handshakeResponse)
            ?: saveNewFactory(actualNodeId, handshakeRequest, handshakeResponse))
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
            // When the entity already exists, its selectors are updated.
            mergeSelectors(factorySelectorRepository, handshakeRequest.tags, entity.selectors, entity.id)

            if (entity.unicastChannel != handshakeResponse.unicastChannel) {
                factoryRepository.save(entity.copy(unicastChannel = handshakeResponse.unicastChannel))
            }
        }

    /**
     * Persists new factory and factory_selector entities
     */
    private suspend fun saveNewFactory(
        actualNodeId: String,
        handshakeRequest: HandshakeRequest,
        handshakeResponse: HandshakeResponse
    ): FactoryEntity {
        val factoryEntity = factoryRepository.save(
            FactoryEntity(
                nodeId = actualNodeId,
                registrationTimestamp = Instant.now(),
                registrationNodeId = handshakeRequest.nodeId,
                unicastChannel = handshakeResponse.unicastChannel
            )
        )
        if (handshakeRequest.tags.isNotEmpty()) {
            factorySelectorRepository.saveAll(handshakeRequest.tags.map { (key, value) ->
                FactorySelectorEntity(factoryEntity.id, key, value)
            })
        }
        return factoryEntity
    }

    /**
     * Creates, updates and deletes scenario-specific entities with accordance to scenarios from handshakeRequest
     */
    private suspend fun saveScenariosAndDependencies(
        registrationScenarios: List<RegistrationScenario>,
        existingFactory: FactoryEntity
    ) {
        val inputScenarios = registrationScenarios.associateBy { it.name }.toMutableMap()
        val existingScenarios = scenarioRepository.findByFactoryId(
            factoryRepository.findTenantReferenceByFactoryIdIn(listOf(existingFactory.id)),
            existingFactory.id
        )
        if (existingScenarios.isNotEmpty()) {
            directedAcyclicGraphRepository.deleteByScenarioIdIn(existingScenarios.map { it.id })
            directedAcyclicGraphSelectorRepository.deleteByDirectedAcyclicGraphIdIn(existingScenarios.flatMap { it.dags }
                .map { it.id })
        }

        val (keptScenarios, obsoleteScenarios) = existingScenarios.partition { it.name in inputScenarios.keys }
        if (obsoleteScenarios.isNotEmpty()) {
            scenarioRepository.deleteAll(obsoleteScenarios)
        }
        val livingScenarios = mutableListOf<ScenarioEntity>()
        if (keptScenarios.isNotEmpty()) {
            livingScenarios += scenarioRepository.updateAll(keptScenarios.map {
                it.copy(enabled = true, defaultMinionsCount = inputScenarios.remove(it.name)!!.minionsCount)
            }).toList()
        }
        if (inputScenarios.isNotEmpty()) {
            livingScenarios += scenarioRepository.saveAll(inputScenarios.map { (_, scenario) ->
                ScenarioEntity(
                    factoryId = existingFactory.id,
                    scenarioName = scenario.name,
                    defaultMinionsCount = scenario.minionsCount
                )
            }).toList()
        }

        val livingScenariosByName = livingScenarios.associateBy { it.name }
        val dagsToSave = prepareDagsOfLivingScenarios(registrationScenarios, livingScenariosByName)
        saveDagsAndSelectors(dagsToSave, registrationScenarios.flatMap { it.directedAcyclicGraphs })
    }

    /**
     * Converts DirectedAcyclicGraphSummary entities to DirectedAcyclicGraphEntity
     */
    private fun prepareDagsOfLivingScenarios(
        registrationScenarios: List<RegistrationScenario>,
        livingScenariosByName: Map<String, ScenarioEntity>
    ): List<DirectedAcyclicGraphEntity> {
        return if (livingScenariosByName.isNotEmpty()) {
            val dagsToSave = registrationScenarios.flatMap { scenario ->
                scenario.directedAcyclicGraphs.map { dag ->
                    DirectedAcyclicGraphEntity(
                        scenarioId = livingScenariosByName[scenario.name]!!.id,
                        name = dag.name,
                        singleton = dag.isSingleton,
                        underLoad = dag.isUnderLoad,
                        numberOfSteps = dag.numberOfSteps,
                        isRoot = dag.isRoot
                    )
                }
            }
            dagsToSave
        } else {
            emptyList()
        }
    }

    /**
     * Saves actual directed_acyclic_graph and directed_acyclic_graph_selector entities
     */
    private suspend fun saveDagsAndSelectors(
        dagsToSave: List<DirectedAcyclicGraphEntity>,
        registrationDags: List<RegistrationDirectedAcyclicGraph>
    ) {
        if (dagsToSave.isNotEmpty()) {
            val dagsEntities = directedAcyclicGraphRepository.saveAll(dagsToSave).toList().associateBy { it.name }
            val dagsSelectorsToSave = registrationDags.flatMap { dag ->
                dag.selectors.map { (key, value) ->
                    DirectedAcyclicGraphSelectorEntity(
                        dagId = dagsEntities[dag.name]!!.id,
                        selectorKey = key,
                        selectorValue = value
                    )
                }
            }
            if (dagsSelectorsToSave.isNotEmpty()) {
                directedAcyclicGraphSelectorRepository.saveAll(dagsSelectorsToSave).collect()
            }
        }
    }

    /**
     * Methods merging selectors of any kind.
     */
    private suspend fun <T : SelectorEntity<*>> mergeSelectors(
        repository: CoroutineCrudRepository<T, Long>,
        newSelectors: Map<String, String>,
        existingSelectors: Collection<T> = emptyList(),
        entityId: Long
    ) {
        val mutableNewSelectors = newSelectors.toMutableMap()
        val (remaining, toDelete) = existingSelectors.partition { it.key in newSelectors.keys }
        if (toDelete.isNotEmpty()) {
            repository.deleteAll(toDelete)
        }
        val toUpdate = remaining.filter { it.value != newSelectors[it.key] }
            .map { it.withValue(mutableNewSelectors.remove(it.key)!!) }
        if (toUpdate.isNotEmpty()) {
            repository.updateAll(toUpdate as List<T>).collect()
        }
        val toSave =
            mutableNewSelectors.filter { newSelector -> remaining.filter { it.key == newSelector.key }.isEmpty() }
                .map { (key, value) -> FactorySelectorEntity(entityId, key, value) }
        if (toSave.isNotEmpty()) {
            repository.saveAll(toSave as Iterable<T>).collect()
        }
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
    override suspend fun getAvailableFactoriesForScenarios(scenarioNames: Collection<String>): Collection<Factory> {
        val scenariosByFactories = scenarioRepository.findActiveByName(scenarioNames).groupBy { it.factoryId }
        val tenantReferencesByFactoryIds = factoryRepository.findTenantReferenceByFactoryIdIn(scenariosByFactories.keys)
        return factoryRepository.getAvailableFactoriesForScenarios(tenantReferencesByFactoryIds, scenarioNames)
            .map { entity ->
                entity.toModel(scenariosByFactories[entity.id]!!.map { it.name })
            }
    }

    @LogInput
    override suspend fun lockFactories(campaignConfiguration: CampaignConfiguration, factories: Collection<NodeId>) {
        if (factories.isNotEmpty()) {
            val factoryIds = factoryRepository.findIdByNodeIdIn(factories)
            if (factoryIds.isNotEmpty()) {
                val campaignName = campaignRepository.findIdByNameAndEndIsNull(
                    factoryRepository.findTenantReferenceByFactoryIdIn(factoryIds), campaignConfiguration.name
                )
                campaignFactoryRepository.saveAll(factoryIds.map { CampaignFactoryEntity(campaignName, it) }).count()
            }
        }
    }

    @LogInput
    override suspend fun releaseFactories(campaignConfiguration: CampaignConfiguration, factories: Collection<NodeId>) {
        if (factories.isNotEmpty()) {
            val factoryIds = factoryRepository.findIdByNodeIdIn(factories)
            if (factoryIds.isNotEmpty()) {
                val campaignName = campaignRepository.findIdByNameAndEndIsNull(
                    factoryRepository.findTenantReferenceByFactoryIdIn(factoryIds), campaignConfiguration.name
                )
                campaignFactoryRepository.discard(campaignName, factoryIds)
            }
        }
    }

    /**
     * getAllScenarios method finds all active scenarios by given ids
     */
    override suspend fun getActiveScenarios(ids: Collection<String>) =
        scenarioRepository.findActiveByName(ids).map(ScenarioEntity::toModel)

}