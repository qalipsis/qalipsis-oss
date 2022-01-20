package io.qalipsis.core.head.persistence

import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.RegistrationScenario
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
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * @author rklymenko
 */
@WithMockk
internal class PersistentFactoryServiceTest {

    @RegisterExtension
    private val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryRepository: FactoryRepository

    @RelaxedMockK
    private lateinit var factorySelectorRepository: FactorySelectorRepository

    @RelaxedMockK
    private lateinit var factoryStateRepository: FactoryStateRepository

    @RelaxedMockK
    private lateinit var scenarioRepository: ScenarioRepository

    @RelaxedMockK
    private lateinit var directedAcyclicGraphRepository: DirectedAcyclicGraphRepository

    @RelaxedMockK
    private lateinit var directedAcyclicGraphSelectorRepository: DirectedAcyclicGraphSelectorRepository

    @InjectMockKs
    private lateinit var persistentFactoryService: PersistentFactoryService

    @Test
    fun `should register new factory without selectors`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val handshakeRequest =
            HandshakeRequest(nodeId = "testNodeId", selectors = emptyMap(), replyTo = "", scenarios = emptyList())
        val now = getTimeMock()
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId
        )
        val factoryStateEntity = FactoryStateEntity(factoryEntity.id, now, 0, FactoryStateValue.REGISTERED, now)
        coEvery { factoryRepository.findByNodeId(actualNodeId) } returns emptyList()
        coEvery { factoryRepository.save(factoryEntity) } returns factoryEntity
        coEvery { factoryStateRepository.save(factoryStateEntity) } returnsArgument 0

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "saveFactory",
            actualNodeId,
            handshakeRequest
        )

        //then
        coVerifyOrder {
            factoryRepository.findByNodeId(actualNodeId)
            factoryRepository.save(factoryEntity)
            factoryStateRepository.save(factoryStateEntity)
        }
        confirmVerified(factoryRepository, factoryStateRepository)
    }

    @Test
    fun `should register new factory with selectors`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            selectors = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = emptyList()
        )
        val now = getTimeMock()
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            selectors = listOf()
        )
        val expectedSelector = FactorySelectorEntity(factoryEntity.id, selectorKey, selectorValue)

        coEvery { factoryRepository.findByNodeId(actualNodeId) } returns emptyList()
        coEvery { factoryRepository.save(factoryEntity) } returns factoryEntity
        coEvery { factorySelectorRepository.saveAll(factoryEntity.selectors) } returns factoryEntity.selectors.asFlow()

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "saveFactory",
            actualNodeId,
            handshakeRequest
        )

        //then
        coVerifyOrder {
            factoryRepository.findByNodeId(actualNodeId)
            factoryRepository.save(factoryEntity)
            factorySelectorRepository.saveAll(listOf(expectedSelector))
        }
        confirmVerified(factoryRepository, factorySelectorRepository)
    }

    @Test
    fun `should update existing factory without selectors`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val handshakeRequest =
            HandshakeRequest(nodeId = "testNodeId", selectors = emptyMap(), replyTo = "", scenarios = emptyList())
        val now = getTimeMock()
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId
        )

        coEvery { factoryRepository.findByNodeId(actualNodeId) } returns listOf(factoryEntity)

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "saveFactory",
            actualNodeId,
            handshakeRequest
        )

        //then
        coVerifyOrder {
            factoryRepository.findByNodeId(actualNodeId)
        }
        coVerifyNever {
            factoryRepository.save(any())
            factorySelectorRepository.saveAll(any<Iterable<FactorySelectorEntity>>())
            factorySelectorRepository.updateAll(any<Iterable<FactorySelectorEntity>>())
            factorySelectorRepository.deleteAll(any())
        }
        confirmVerified(factoryRepository, factorySelectorRepository)
    }

    @Test
    fun `should save new selectors`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            selectors = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = emptyList()
        )
        val now = getTimeMock()
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            selectors = listOf()
        )
        val newSelector = FactorySelectorEntity(factoryEntity.id, selectorKey, selectorValue)

        coEvery { factorySelectorRepository.saveAll(listOf(newSelector)) } returns flowOf(newSelector)

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "mergeSelectors",
            factorySelectorRepository,
            handshakeRequest.selectors,
            factoryEntity.selectors,
            factoryEntity.id
        )

        //then
        val expectedSelector = FactorySelectorEntity(factoryEntity.id, selectorKey, selectorValue)
        coVerifyOnce {
            factorySelectorRepository.saveAll(listOf(expectedSelector))
        }
        coVerifyNever {
            factorySelectorRepository.updateAll(any<Iterable<FactorySelectorEntity>>())
            factorySelectorRepository.deleteAll(any())
        }
        confirmVerified(factorySelectorRepository)
    }

    @Test
    fun `should update existing selectors`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"
        val selectorNewValue = "test-selector-new-value"
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            selectors = mapOf(selectorKey to selectorNewValue),
            replyTo = "",
            scenarios = emptyList()
        )
        val now = getTimeMock()
        val selector = FactorySelectorEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            selectors = listOf(selector)
        )

        coEvery { factorySelectorRepository.updateAll(listOf(selector)) } returns flowOf(selector)

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "mergeSelectors",
            factorySelectorRepository,
            handshakeRequest.selectors,
            factoryEntity.selectors,
            factoryEntity.id
        )

        //then
        val expectedSelector = FactorySelectorEntity(factoryEntity.id, selectorKey, selectorNewValue)
        coVerifyOnce {
            factorySelectorRepository.updateAll(listOf(expectedSelector))
        }
        coVerifyNever {
            factorySelectorRepository.saveAll(any<Iterable<FactorySelectorEntity>>())
            factorySelectorRepository.deleteAll(any())
        }
        confirmVerified(factorySelectorRepository)
    }

    @Test
    fun `should delete existing selectors`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"
        val handshakeRequest =
            HandshakeRequest(nodeId = "testNodeId", selectors = emptyMap(), replyTo = "", scenarios = emptyList())
        val now = getTimeMock()
        val selector = FactorySelectorEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            selectors = listOf(selector)
        )

        coEvery { factorySelectorRepository.deleteAll(listOf(selector)) } returns 1

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "mergeSelectors",
            factorySelectorRepository,
            handshakeRequest.selectors,
            factoryEntity.selectors,
            factoryEntity.id
        )

        //then
        coVerifyOnce {
            factorySelectorRepository.deleteAll(listOf(selector))
        }
        coVerifyNever {
            factorySelectorRepository.updateAll(any<Iterable<FactorySelectorEntity>>())
            factorySelectorRepository.saveAll(any<Iterable<FactorySelectorEntity>>())
        }
        confirmVerified(factorySelectorRepository)
    }

    @Test
    fun `should skip update of existing selectors because they were not changed`() =
        testDispatcherProvider.run {
            //given
            val actualNodeId = "boo"
            val selectorKey = "test-selector-key"
            val selectorValue = "test-selector-value"
            val handshakeRequest = HandshakeRequest(
                nodeId = "testNodeId",
                selectors = mapOf(selectorKey to selectorValue),
                replyTo = "",
                scenarios = emptyList()
            )
            val now = getTimeMock()
            val selector = FactorySelectorEntity(-1, selectorKey, selectorValue)
            val factoryEntity = FactoryEntity(
                nodeId = actualNodeId,
                registrationTimestamp = now,
                registrationNodeId = handshakeRequest.nodeId,
                selectors = listOf(selector)
            )

            // when
            persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
                "mergeSelectors",
                factorySelectorRepository,
                handshakeRequest.selectors,
                factoryEntity.selectors,
                factoryEntity.id
            )

            //then
            coVerifyNever {
                factorySelectorRepository.updateAll(any<Iterable<FactorySelectorEntity>>())
            }
            confirmVerified(factorySelectorRepository)
        }

    @Test
    fun `should create new selectors and update existing selectors and delete old selectors and dont touch unchanged selectors`() =
        testDispatcherProvider.run {
            //given
            val actualNodeId = "boo"
            val existingSelectorsMap =
                mapOf("test0key" to "test0value", "test2key" to "test002value", "test4key" to "test4value")
            val newSelectorsMap =
                mapOf("test1key" to "test1value", "test2key" to "test2value", "test4key" to "test4value")
            val handshakeRequest = HandshakeRequest(
                nodeId = "testNodeId",
                selectors = newSelectorsMap,
                replyTo = "",
                scenarios = emptyList()
            )
            val now = getTimeMock()
            val existingSelectors = existingSelectorsMap.map { FactorySelectorEntity(-1, it.key, it.value) }
            val factoryEntity = FactoryEntity(
                nodeId = actualNodeId,
                registrationTimestamp = now,
                registrationNodeId = handshakeRequest.nodeId,
                selectors = existingSelectors
            )

            coEvery { factorySelectorRepository.deleteAll(listOf(existingSelectors[0])) } returns 1
            coEvery {
                factorySelectorRepository.updateAll(
                    listOf(
                        FactorySelectorEntity(
                            factoryEntity.id,
                            "test2key",
                            newSelectorsMap["test2key"]!!
                        )
                    )
                )
            } returns flowOf()
            coEvery {
                factorySelectorRepository.saveAll(
                    listOf(
                        FactorySelectorEntity(
                            factoryEntity.id,
                            "test1key",
                            "test1value"
                        )
                    )
                )
            } returns flowOf()

            // when
            persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
                "mergeSelectors",
                factorySelectorRepository,
                handshakeRequest.selectors,
                factoryEntity.selectors,
                factoryEntity.id
            )

            //then
            coVerifyOrder {
                factorySelectorRepository.deleteAll(listOf(existingSelectors[0]))
                factorySelectorRepository.updateAll(
                    listOf(
                        FactorySelectorEntity(
                            factoryEntity.id,
                            "test2key",
                            newSelectorsMap["test2key"]!!
                        )
                    )
                )
                factorySelectorRepository.saveAll(
                    listOf(
                        FactorySelectorEntity(
                            factoryEntity.id,
                            "test1key",
                            "test1value"
                        )
                    )
                )
            }
            confirmVerified(factorySelectorRepository)
        }

    @Test
    fun `should create new scenario and dags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"

        val graphSummary = DirectedAcyclicGraphSummary(id = "new-test-dag-id")
        val newRegistrationScenario = RegistrationScenario(
            id = "new-test-scenario",
            minionsCount = 1,
            directedAcyclicGraphs = listOf(graphSummary)
        )
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            selectors = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario)
        )
        val now = getTimeMock()
        val selector = FactorySelectorEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            selectors = listOf(selector)
        )
        val dag = DirectedAcyclicGraphSummary(id = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag, name = newRegistrationScenario.id)

        coEvery { factoryRepository.findByNodeId(actualNodeId) } returns listOf(factoryEntity)
        coEvery { scenarioRepository.findByFactoryId(factoryEntity.id) } returns emptyList()
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.deleteAll(any()) } returns 1
        coEvery { scenarioRepository.saveAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)
        coEvery { directedAcyclicGraphRepository.deleteAll(any()) } returns 1
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf()

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "saveScenariosAndDependencies",
            handshakeRequest.scenarios,
            factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId(factoryEntity.id)
            scenarioRepository.saveAll(any<Iterable<ScenarioEntity>>())
            directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>())
        }
        confirmVerified(
            factoryRepository,
            scenarioRepository,
            factoryStateRepository,
            directedAcyclicGraphRepository
        )
    }

    @Test
    fun `should update scenario and dags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"

        val graphSummary = DirectedAcyclicGraphSummary(id = "new-test-dag-id")
        val newRegistrationScenario = RegistrationScenario(
            id = "test",
            minionsCount = 1,
            directedAcyclicGraphs = listOf(graphSummary)
        )
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            selectors = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario)
        )
        val now = getTimeMock()
        val selector = FactorySelectorEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            selectors = listOf(selector)
        )
        val dag = DirectedAcyclicGraphSummary(id = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag)

        coEvery { scenarioRepository.findByFactoryId(factoryEntity.id) } returns listOf(scenarioEntity)
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)
        coEvery { directedAcyclicGraphRepository.deleteAll(any()) } returns 1
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf()

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "saveScenariosAndDependencies",
            handshakeRequest.scenarios,
            factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId(factoryEntity.id)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id))
            scenarioRepository.updateAll(listOf(scenarioEntity.copy(enabled = true)))
            directedAcyclicGraphRepository.saveAll(handshakeRequest.scenarios.flatMap { it.directedAcyclicGraphs }
                .map { dag ->
                    DirectedAcyclicGraphEntity(
                        scenarioId = 1,
                        name = dag.id,
                        singleton = dag.isSingleton,
                        underLoad = dag.isUnderLoad,
                        numberOfSteps = dag.numberOfSteps,
                        version = now,
                        isRoot = false
                    )
                })
        }
        confirmVerified(
            scenarioRepository,
            directedAcyclicGraphRepository
        )
    }

    @Test
    fun `should update scenario and dags and dag selectors`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"

        val graphSummary = DirectedAcyclicGraphSummary(
            id = "new-test-dag-id",
            selectors = mapOf("test_dag_selector1" to "test_dag_selector_value1")
        )
        val newRegistrationScenario = RegistrationScenario(
            id = "test",
            minionsCount = 1,
            directedAcyclicGraphs = listOf(graphSummary)
        )
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            selectors = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario)
        )
        val now = getTimeMock()
        val selector = FactorySelectorEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            selectors = listOf(selector)
        )
        val dag = DirectedAcyclicGraphSummary(id = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag)
        val savedDag = DirectedAcyclicGraphEntity(
            scenarioId = scenarioEntity.id,
            name = graphSummary.id,
            isRoot = graphSummary.isRoot,
            singleton = true,
            underLoad = true,
            numberOfSteps = 1,
            version = now,
            selectors = graphSummary.selectors.map { DirectedAcyclicGraphSelectorEntity(1, it.key, it.value) })

        coEvery { scenarioRepository.findByFactoryId(factoryEntity.id) } returns listOf(scenarioEntity)
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)
        coEvery { directedAcyclicGraphRepository.deleteAll(any()) } returns 1
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf(
            savedDag
        )

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "saveScenariosAndDependencies",
            handshakeRequest.scenarios,
            factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId(factoryEntity.id)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id))
            directedAcyclicGraphSelectorRepository.deleteByDirectedAcyclicGraphIdIn(scenarioEntity.dags.map { it.id })
            scenarioRepository.updateAll(listOf(scenarioEntity.copy(enabled = true)))
            directedAcyclicGraphRepository.saveAll(handshakeRequest.scenarios.flatMap { it.directedAcyclicGraphs }
                .map { dag ->
                    DirectedAcyclicGraphEntity(
                        scenarioId = 1,
                        name = dag.id,
                        singleton = dag.isSingleton,
                        underLoad = dag.isUnderLoad,
                        numberOfSteps = dag.numberOfSteps,
                        version = now,
                        isRoot = false
                    )
                })
            directedAcyclicGraphSelectorRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphSelectorEntity(
                        id = -1,
                        directedAcyclicGraphId = -1,
                        key = "test_dag_selector1",
                        value = graphSummary.selectors["test_dag_selector1"]!!
                    )
                )
            )
        }
        confirmVerified(
            scenarioRepository,
            directedAcyclicGraphRepository,
            directedAcyclicGraphSelectorRepository
        )
    }

    @Test
    fun `should save dags and dag selectors`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val graphSummary = DirectedAcyclicGraphSummary(
            id = "new-test-dag-id",
            selectors = mapOf("test_dag_selector1" to "test_dag_selector_value1")
        )
        val directedAcyclicGraphs = listOf(graphSummary)
        val savedDag = DirectedAcyclicGraphEntity(
            scenarioId = 1,
            name = graphSummary.id,
            isRoot = false,
            singleton = true,
            underLoad = true,
            numberOfSteps = 1,
            version = now,
            selectors = graphSummary.selectors.map { DirectedAcyclicGraphSelectorEntity(1, it.key, it.value) })
        val expectedDagEntity = DirectedAcyclicGraphEntity(
            scenarioId = 1,
            name = "new-test-dag-id",
            isRoot = false,
            singleton = true,
            underLoad = true,
            numberOfSteps = 1,
            version = now,
            selectors = listOf(
                DirectedAcyclicGraphSelectorEntity(
                    id = -1,
                    directedAcyclicGraphId = 1,
                    key = "test_dag_selector1",
                    value = "test_dag_selector_value1"
                )
            )
        )

        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf(
            savedDag
        )

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "saveDagsAndSelectors",
            listOf(savedDag),
            directedAcyclicGraphs
        )

        //then
        coVerifyOrder {

            directedAcyclicGraphRepository.saveAll(listOf(expectedDagEntity))
            directedAcyclicGraphSelectorRepository.saveAll(expectedDagEntity.selectors.map {
                it.copy(
                    directedAcyclicGraphId = savedDag.id
                )
            })
        }
        confirmVerified(
            directedAcyclicGraphRepository,
            directedAcyclicGraphSelectorRepository
        )
    }

    @Test
    fun `should save dags without dag selectors`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val graphSummary = DirectedAcyclicGraphSummary(id = "new-test-dag-id")
        val directedAcyclicGraphs = listOf(graphSummary)
        val savedDag = DirectedAcyclicGraphEntity(
            scenarioId = 1,
            name = graphSummary.id,
            isRoot = false,
            singleton = true,
            underLoad = true,
            numberOfSteps = 1,
            version = now
        )
        val expectedDagEntity = DirectedAcyclicGraphEntity(
            scenarioId = 1,
            name = "new-test-dag-id",
            isRoot = false,
            singleton = true,
            underLoad = true,
            numberOfSteps = 1,
            version = now,
            selectors = emptyList()
        )

        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf(
            savedDag
        )

        // when
        persistentFactoryService.coInvokeInvisible<PersistentFactoryService>(
            "saveDagsAndSelectors",
            listOf(savedDag),
            directedAcyclicGraphs
        )

        //then
        coVerifyOrder {
            directedAcyclicGraphRepository.saveAll(listOf(expectedDagEntity))
        }
        coVerifyNever {
            directedAcyclicGraphSelectorRepository.saveAll(any<Iterable<DirectedAcyclicGraphSelectorEntity>>())
        }
        confirmVerified(
            directedAcyclicGraphRepository,
            directedAcyclicGraphSelectorRepository
        )
    }


    @Test
    fun `should update existing scenario and dags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"

        val graphSummary = DirectedAcyclicGraphSummary(id = "new-test-dag-id")
        val newRegistrationScenario =
            RegistrationScenario(id = "test", minionsCount = 1, directedAcyclicGraphs = listOf(graphSummary))
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            selectors = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario)
        )
        val now = getTimeMock()
        val selector = FactorySelectorEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            selectors = listOf(selector)
        )

        val dag = DirectedAcyclicGraphSummary(id = "test", isSingleton = true, isUnderLoad = true)
        val expectedDagEntity = convertDagSummaryToEntity(dag)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag, true)

        coEvery { factoryRepository.findByNodeId(actualNodeId) } returns listOf(factoryEntity)
        coEvery { scenarioRepository.findByFactoryId(factoryEntity.id) } returns listOf(scenarioEntity)
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)

        // when
        persistentFactoryService.register(actualNodeId, handshakeRequest)

        //then
        coVerifyOrder {
            factoryRepository.findByNodeId(actualNodeId)
            factoryStateRepository.save(any())
            scenarioRepository.findByFactoryId(factoryEntity.id)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id))
            scenarioRepository.updateAll(listOf(scenarioEntity))
            directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>())
        }
        confirmVerified(
            factoryRepository,
            scenarioRepository,
            factoryStateRepository,
            directedAcyclicGraphRepository
        )
    }

    @Test
    fun `should return all scenarios`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val dag = DirectedAcyclicGraphSummary(id = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntities = listOf(
            ScenarioEntity(
                id = 1,
                version = now,
                factoryId = 1,
                name = "test",
                defaultMinionsCount = 1,
                enabled = true,
                dags = listOf(
                    DirectedAcyclicGraphEntity(
                        -1,
                        dag.id,
                        dag.isRoot,
                        dag.isSingleton,
                        dag.isUnderLoad,
                        dag.numberOfSteps,
                        emptyList()
                    )
                )
            )
        )
        val ids = scenarioEntities.map { it.name }
        coEvery { scenarioRepository.findActiveByName(ids) } returns scenarioEntities


        //when
        val scenarios = persistentFactoryService.getAllScenarios(ids)

        //then
        coVerifyOnce {
            scenarioRepository.findActiveByName(ids)
        }

        Assert.assertEquals(
            scenarioEntities.map {
                ScenarioSummary(id = it.name,
                    minionsCount = it.defaultMinionsCount,
                    directedAcyclicGraphs = it.dags.map {
                        DirectedAcyclicGraphSummary(
                            id = it.id.toString(),
                            isSingleton = it.singleton,
                            isRoot = false,
                            isUnderLoad = it.underLoad,
                            numberOfSteps = it.numberOfSteps,
                            selectors = emptyMap()
                        )
                    })
            }, scenarios
        )

        confirmVerified(scenarioRepository)
    }

    @Test
    fun `should update heartbeat`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()
        val factoryId = 1L
        val heartbeat = Heartbeat(nodeId = "boo", timestamp = now, state = Heartbeat.STATE.UNREGISTERED, campaignId = "1")

        coEvery { factoryRepository.findFactoryIdByNodeId(heartbeat.nodeId) } returns factoryId

        //when
        persistentFactoryService.updateHeartbeat(heartbeat)

        //then
        coVerifyOrder {
            factoryRepository.findFactoryIdByNodeId(heartbeat.nodeId)
            factoryStateRepository.save(FactoryStateEntity(factoryId, heartbeat.timestamp, 0, FactoryStateValue.valueOf(heartbeat.state.name), now))
        }

        confirmVerified(factoryRepository,
                        factoryStateRepository)
    }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }

    private fun createScenario(
        now: Instant,
        factoryId: Long,
        dag: DirectedAcyclicGraphSummary,
        enabled: Boolean = false,
        name: String = "test"
    ) =
        ScenarioEntity(
            id = 1,
            version = now,
            factoryId = factoryId,
            name = name,
            defaultMinionsCount = 1,
            enabled = enabled,
            dags = listOf(
                DirectedAcyclicGraphEntity(
                    -1,
                    dag.id,
                    dag.isRoot,
                    dag.isSingleton,
                    dag.isUnderLoad,
                    dag.numberOfSteps,
                    dag.selectors.map { (key, value) -> DirectedAcyclicGraphSelectorEntity(-1, key, value) })
            )
        )

    private fun convertDagSummaryToEntity(dag: DirectedAcyclicGraphSummary) =
        DirectedAcyclicGraphEntity(
            -1,
            dag.id,
            dag.isRoot,
            dag.isSingleton,
            dag.isUnderLoad,
            dag.numberOfSteps,
            dag.selectors.map { (key, value) -> DirectedAcyclicGraphSelectorEntity(-1, key, value) }
        )
}