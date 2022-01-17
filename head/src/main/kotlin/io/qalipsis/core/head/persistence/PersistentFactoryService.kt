package io.qalipsis.core.head.persistence

import io.micronaut.context.annotation.Requires
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.RegistrationDirectedAcyclicGraph
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.persistence.entity.DirectedAcyclicGraphEntity
import io.qalipsis.core.head.persistence.entity.DirectedAcyclicGraphSelectorEntity
import io.qalipsis.core.head.persistence.entity.FactoryEntity
import io.qalipsis.core.head.persistence.entity.FactorySelectorEntity
import io.qalipsis.core.head.persistence.entity.FactoryStateEntity
import io.qalipsis.core.head.persistence.entity.FactoryStateValue
import io.qalipsis.core.head.persistence.entity.ScenarioEntity
import io.qalipsis.core.head.persistence.repository.DirectedAcyclicGraphRepository
import io.qalipsis.core.head.persistence.repository.DirectedAcyclicGraphSelectorRepository
import io.qalipsis.core.head.persistence.repository.FactoryRepository
import io.qalipsis.core.head.persistence.repository.FactorySelectorRepository
import io.qalipsis.core.head.persistence.repository.FactoryStateRepository
import io.qalipsis.core.head.persistence.repository.ScenarioRepository
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import java.time.Instant
import javax.transaction.Transactional

/**
 * FactoryService implementation for persistence implementation of factory-specific data
 * FactoryRepository, FactorySelectorRepository, FactoryStateRepository, ScenarioRepository, DirectedAcyclicGraphRepository, DirectedAcyclicGraphSelectorRepository
 * beans should be injected
 *
 * @author rklymenko
 */
@Singleton
@Requires(notEnv = [ExecutionEnvironments.VOLATILE])
internal class PersistentFactoryService(
    private val factoryRepository: FactoryRepository,
    private val factorySelectorRepository: FactorySelectorRepository,
    private val factoryStateRepository: FactoryStateRepository,
    private val scenarioRepository: ScenarioRepository,
    private val directedAcyclicGraphRepository: DirectedAcyclicGraphRepository,
    private val directedAcyclicGraphSelectorRepository: DirectedAcyclicGraphSelectorRepository
) : FactoryService {

    /**
     * Creates and updates factory-specific information using handhakeRequest     *
     */
    @Transactional
    override suspend fun register(actualNodeid: String, handshakeRequest: HandshakeRequest) {
        val existingFactory = saveFactory(actualNodeid, handshakeRequest)
        saveScenariosAndDependencies(handshakeRequest.scenarios, existingFactory)
    }

    /**
     * Updates or saves factory and factory_selector entities using handshakeRequest
     */
    private suspend fun saveFactory(actualNodeId: String, handshakeRequest: HandshakeRequest) =
        (updateFactory(actualNodeId, handshakeRequest) ?: saveNewFactory(
            actualNodeId,
            handshakeRequest
        )).also { factoryEntity ->
            // The state of the factory is changed to REGISTERED.
            val now = Instant.now()
            factoryStateRepository.save(
                FactoryStateEntity(
                    factoryEntity.id,
                    now,
                    FactoryStateValue.REGISTERED,
                    now
                )
            )
        }

    /**
     * Updates existing factory using factory_selector entities from handshakeRequest
     */
    private suspend fun updateFactory(
        actualNodeId: String,
        handshakeRequest: HandshakeRequest
    ) = factoryRepository.findByNodeId(actualNodeId).firstOrNull()?.also { entity ->
        // When the entity already exists, its selectors are updated.
        mergeSelectors(
            factorySelectorRepository,
            handshakeRequest.selectors,
            entity.selectors,
            entity.id
        )
    }

    /**
     * Persists new factory and factory_selector entities
     */
    private suspend fun saveNewFactory(
        actualNodeId: String,
        handshakeRequest: HandshakeRequest
    ) = factoryRepository.save(
        FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = Instant.now(),
            registrationNodeId = handshakeRequest.nodeId
        )
    ).also { factoryEntity ->
        if (handshakeRequest.selectors.isNotEmpty()) {
            factorySelectorRepository.saveAll(handshakeRequest.selectors.map { (key, value) ->
                FactorySelectorEntity(factoryEntity.id, key, value)
            })
        }
    }

    /**
     * Creates, updates and deletes scenario-specific entities with accordance to scenarios from handshakeRequest
     */
    private suspend fun saveScenariosAndDependencies(
        registrationScenarios: List<RegistrationScenario>,
        existingFactory: FactoryEntity
    ) {
        val inputScenarios = registrationScenarios.associateBy { it.id }.toMutableMap()
        val existingScenarios = scenarioRepository.findByFactoryId(existingFactory.id)
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
                it.copy(
                    enabled = true,
                    defaultMinionsCount = inputScenarios.remove(it.name)!!.minionsCount
                )
            }).toList()
        }
        if (inputScenarios.isNotEmpty()) {
            livingScenarios += scenarioRepository.saveAll(inputScenarios.map { (_, scenario) ->
                ScenarioEntity(
                    factoryId = existingFactory.id,
                    scenarioName = scenario.id,
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
            val dagsToSave = registrationScenarios.flatMapIndexed { index, scenario ->
                scenario.directedAcyclicGraphs.map { dag ->
                    DirectedAcyclicGraphEntity(
                        scenarioId = livingScenariosByName[scenario.id]!!.id,
                        name = dag.id,
                        singleton = dag.isSingleton,
                        underLoad = dag.isUnderLoad,
                        numberOfSteps = dag.numberOfSteps
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
                        dagId = dagsEntities[dag.id]!!.id,
                        selectorKey = key,
                        selectorValue = value
                    )
                }
            }
            if (dagsSelectorsToSave.isNotEmpty()) {
                directedAcyclicGraphSelectorRepository.saveAll(dagsSelectorsToSave).toList()
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
            repository.updateAll(toUpdate as List<T>)
        }
        val toSave =
            mutableNewSelectors.filter { newSelector -> remaining.filter { it.key == newSelector.key }.isEmpty() }
                .map { (key, value) -> FactorySelectorEntity(entityId, key, value) }
        if (toSave.isNotEmpty()) {
            repository.saveAll(toSave as Iterable<T>)
        }
    }

    /**
     * updateHeartbeat method updates factory_state with given STATE
     */
    override suspend fun updateHeartbeat(heartbeat: Heartbeat) {
        TODO("Not yet implemented: https://aeris-consulting.atlassian.net/browse/QALI-138")
    }

    /**
     * getAllScenarios method finds all active scenarios by given ids
     */
    override suspend fun getAllScenarios(ids: Collection<String>) = scenarioRepository.findActiveByName(ids).map {
        ScenarioSummary(
            id = it.name,
            minionsCount = it.defaultMinionsCount,
            directedAcyclicGraphs = it.dags.map {
                // todo: convert selectors, find isRoot value
                DirectedAcyclicGraphSummary(
                    id = it.id.toString(),
                    isSingleton = it.singleton,
                    isRoot = false,
                    isUnderLoad = it.underLoad,
                    numberOfSteps = it.numberOfSteps,
                    selectors = emptyMap()
                )
            }
        )
    }
}