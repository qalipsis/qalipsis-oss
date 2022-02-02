package io.qalipsis.core.head.factory

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.head.campaign.CampaignConfiguration
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
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
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
internal class ClusterFactoryServiceTest {

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

    @RelaxedMockK
    private lateinit var campaignRepository: CampaignRepository

    @RelaxedMockK
    private lateinit var campaignFactoryRepository: CampaignFactoryRepository

    @InjectMockKs
    private lateinit var clusterFactoryService: ClusterFactoryService

    @Test
    fun `should register new factory without selectors`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val handshakeRequest =
            HandshakeRequest(nodeId = "testNodeId", selectors = emptyMap(), replyTo = "", scenarios = emptyList())
        val now = getTimeMock()
        coEvery { factoryRepository.findByNodeIdIn(listOf(actualNodeId)) } returns emptyList()
        coEvery { factoryRepository.save(any()) } returns relaxedMockk { every { id } returns 123 }

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveFactory",
            actualNodeId,
            handshakeRequest
        )

        //then
        coVerifyOrder {
            factoryRepository.findByNodeIdIn(listOf(actualNodeId))
            factoryRepository.save(
                FactoryEntity(
                    nodeId = actualNodeId,
                    registrationTimestamp = now,
                    registrationNodeId = handshakeRequest.nodeId,
                    unicastChannel = "directives-unicast-boo",
                    broadcastChannel = "directives-broadcast"
                )
            )
            factoryStateRepository.save(FactoryStateEntity(now, 123, now, 0, FactoryStateValue.REGISTERED))
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
        coEvery { factoryRepository.findByNodeIdIn(listOf(actualNodeId)) } returns emptyList()
        coEvery { factoryRepository.save(any()) } returns relaxedMockk { every { id } returns 123 }

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveFactory",
            actualNodeId,
            handshakeRequest
        )

        //then
        coVerifyOrder {
            factoryRepository.findByNodeIdIn(listOf(actualNodeId))
            factoryRepository.save(
                FactoryEntity(
                    nodeId = actualNodeId,
                    registrationTimestamp = now,
                    registrationNodeId = handshakeRequest.nodeId,
                    unicastChannel = "directives-unicast-boo",
                    broadcastChannel = "directives-broadcast"
                )
            )
            factorySelectorRepository.saveAll(listOf(FactorySelectorEntity(123, selectorKey, selectorValue)))
            factoryStateRepository.save(FactoryStateEntity(now, 123, now, 0, FactoryStateValue.REGISTERED))
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
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast",
            broadcastChannel = "broadcast"
        )

        coEvery { factoryRepository.findByNodeIdIn(listOf(actualNodeId)) } returns listOf(factoryEntity)

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveFactory",
            actualNodeId,
            handshakeRequest
        )

        //then
        coVerifyOrder {
            factoryRepository.findByNodeIdIn(listOf(actualNodeId))
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
            unicastChannel = "unicast",
            broadcastChannel = "broadcast",
            selectors = listOf()
        )
        val newSelector = FactorySelectorEntity(factoryEntity.id, selectorKey, selectorValue)

        coEvery { factorySelectorRepository.saveAll(listOf(newSelector)) } returns flowOf(newSelector)

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
            unicastChannel = "unicast",
            broadcastChannel = "broadcast",
            selectors = listOf(selector)
        )

        coEvery { factorySelectorRepository.updateAll(listOf(selector)) } returns flowOf(selector)

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
            unicastChannel = "unicast",
            broadcastChannel = "broadcast",
            selectors = listOf(selector)
        )

        coEvery { factorySelectorRepository.deleteAll(listOf(selector)) } returns 1

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
                unicastChannel = "unicast",
                broadcastChannel = "broadcast",
                selectors = listOf(selector)
            )

            // when
            clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
                unicastChannel = "unicast",
                broadcastChannel = "broadcast",
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
            clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
            unicastChannel = "unicast",
            broadcastChannel = "broadcast",
            selectors = listOf(selector)
        )
        val dag = DirectedAcyclicGraphSummary(id = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag, name = newRegistrationScenario.id)

        coEvery { factoryRepository.findByNodeIdIn(listOf(actualNodeId)) } returns listOf(factoryEntity)
        coEvery { scenarioRepository.findByFactoryId(factoryEntity.id) } returns emptyList()
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.deleteAll(any()) } returns 1
        coEvery { scenarioRepository.saveAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)
        coEvery { directedAcyclicGraphRepository.deleteAll(any()) } returns 1
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf()

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
            unicastChannel = "unicast",
            broadcastChannel = "broadcast",
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
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
            unicastChannel = "unicast",
            broadcastChannel = "broadcast",
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
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
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
            unicastChannel = "unicast",
            broadcastChannel = "broadcast",
            selectors = listOf(selector)
        )

        val dag = DirectedAcyclicGraphSummary(id = "test", isSingleton = true, isUnderLoad = true)
        val expectedDagEntity = convertDagSummaryToEntity(dag)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag, true)

        coEvery { factoryRepository.findByNodeIdIn(listOf(actualNodeId)) } returns listOf(factoryEntity)
        coEvery { scenarioRepository.findByFactoryId(factoryEntity.id) } returns listOf(scenarioEntity)
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)

        // when
        clusterFactoryService.register(actualNodeId, handshakeRequest)

        //then
        coVerifyOrder {
            factoryRepository.findByNodeIdIn(listOf(actualNodeId))
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
    fun `should return all active scenarios`() = testDispatcherProvider.run {
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
        val scenarios = clusterFactoryService.getActiveScenarios(ids)

        //then
        coVerifyOnce {
            scenarioRepository.findActiveByName(ids)
        }

        Assert.assertEquals(
            scenarioEntities.map { scenario ->
                ScenarioSummary(id = scenario.name,
                    minionsCount = scenario.defaultMinionsCount,
                    directedAcyclicGraphs = scenario.dags.map { dag ->
                        DirectedAcyclicGraphSummary(
                            id = dag.name,
                            isSingleton = dag.singleton,
                            isRoot = false,
                            isUnderLoad = dag.underLoad,
                            numberOfSteps = dag.numberOfSteps,
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
        val heartbeat =
            Heartbeat(nodeId = "boo", timestamp = now, state = Heartbeat.STATE.UNREGISTERED, campaignId = "1")

        coEvery { factoryRepository.findIdByNodeIdIn(listOf(heartbeat.nodeId)) } returns listOf(factoryId)

        //when
        clusterFactoryService.updateHeartbeat(heartbeat)

        //then
        coVerifyOrder {
            factoryRepository.findIdByNodeIdIn(listOf(heartbeat.nodeId))
            factoryStateRepository.save(
                FactoryStateEntity(
                    now,
                    factoryId,
                    heartbeat.timestamp,
                    0,
                    FactoryStateValue.valueOf(heartbeat.state.name)
                )
            )
        }

        confirmVerified(factoryRepository, factoryStateRepository)
    }

    @Test
    internal fun `should return the available factories by scenarios`() = testDispatcherProvider.run {
        // given
        val scenarios = listOf("scenario-1", "scenario-2")
        coEvery { scenarioRepository.findActiveByName(refEq(scenarios)) } returns listOf(
            mockk { every { name } returns "scenario-1"; every { factoryId } returns 1 },
            mockk { every { name } returns "scenario-2"; every { factoryId } returns 1 },
            mockk { every { name } returns "scenario-2"; every { factoryId } returns 2 }
        )
        val factory1 = mockk<Factory>()
        val factory2 = mockk<Factory>()
        val factoryEntity1 = mockk<FactoryEntity> {
            every { id } returns 1; every { toModel(any()) } returns factory1
        }
        val factoryEntity2 = mockk<FactoryEntity> {
            every { id } returns 2; every { toModel(any()) } returns factory2
        }
        coEvery { factoryRepository.getAvailableFactoriesForScenarios(refEq(scenarios)) } returns listOf(
            factoryEntity1, factoryEntity2
        )

        // when
        val result = clusterFactoryService.getAvailableFactoriesForScenarios(scenarios)

        // then
        assertThat(result).all {
            hasSize(2)
            containsOnly(factory1, factory2)
        }
        verifyOnce {
            factoryEntity1.toModel(listOf("scenario-1", "scenario-2"))
            factoryEntity2.toModel(listOf("scenario-2"))
        }
    }

    @Test
    internal fun `should lock the specified factories on the specified campaign`() = testDispatcherProvider.run {
        // given
        val factories = listOf("factory-1", "factory-2")
        coEvery { factoryRepository.findIdByNodeIdIn(refEq(factories)) } returns listOf(12, 32)
        coEvery { campaignRepository.findIdByNameAndEndIsNull("my-campaign") } returns 765
        val campaignConfiguration = mockk<CampaignConfiguration> { every { id } returns "my-campaign" }

        // when
        clusterFactoryService.lockFactories(campaignConfiguration, factories)

        // then
        coVerifyOrder {
            factoryRepository.findIdByNodeIdIn(refEq(factories))
            campaignRepository.findIdByNameAndEndIsNull("my-campaign")
            campaignFactoryRepository.saveAll(
                listOf(
                    CampaignFactoryEntity(765, 12),
                    CampaignFactoryEntity(765, 32)
                )
            )
            // TODO Unfortunately we cannot verify that a collect operation was called on the returned value of saveAll.
        }
        confirmVerified(factoryRepository, campaignRepository, campaignFactoryRepository)
    }

    @Test
    internal fun `should do no lock when no factory is provided`() = testDispatcherProvider.run {
        // given
        val factories = listOf("factory-1", "factory-2")
        coEvery { factoryRepository.findIdByNodeIdIn(refEq(factories)) } returns emptyList()
        val campaignConfiguration = mockk<CampaignConfiguration> { every { id } returns "my-campaign" }

        // when
        clusterFactoryService.lockFactories(campaignConfiguration, factories)

        // then
        coVerifyOrder {
            factoryRepository.findIdByNodeIdIn(refEq(factories))
        }
        confirmVerified(factoryRepository, campaignRepository, campaignFactoryRepository)
    }

    @Test
    internal fun `should do no lock when no factory is found`() = testDispatcherProvider.run {
        // when
        clusterFactoryService.lockFactories(mockk(), emptyList())

        // then
        confirmVerified(factoryRepository, campaignRepository, campaignFactoryRepository)
    }

    @Test
    internal fun `should release the specified factories on the specified campaign`() = testDispatcherProvider.run {
        // given
        val factories = listOf("factory-1", "factory-2")
        coEvery { factoryRepository.findIdByNodeIdIn(refEq(factories)) } returns listOf(12, 32)
        coEvery { campaignRepository.findIdByNameAndEndIsNull("my-campaign") } returns 765
        val campaignConfiguration = mockk<CampaignConfiguration> { every { id } returns "my-campaign" }

        // when
        clusterFactoryService.releaseFactories(campaignConfiguration, factories)

        // then
        coVerifyOrder {
            factoryRepository.findIdByNodeIdIn(refEq(factories))
            campaignRepository.findIdByNameAndEndIsNull("my-campaign")
            campaignFactoryRepository.discard(765, listOf(12, 32))
        }
        confirmVerified(factoryRepository, campaignRepository, campaignFactoryRepository)
    }

    @Test
    internal fun `should do no release when no factory is provided`() = testDispatcherProvider.run {
        // given
        val factories = listOf("factory-1", "factory-2")
        coEvery { factoryRepository.findIdByNodeIdIn(refEq(factories)) } returns emptyList()
        val campaignConfiguration = mockk<CampaignConfiguration> { every { id } returns "my-campaign" }

        // when
        clusterFactoryService.releaseFactories(campaignConfiguration, factories)

        // then
        coVerifyOrder {
            factoryRepository.findIdByNodeIdIn(refEq(factories))
        }
        confirmVerified(factoryRepository, campaignRepository, campaignFactoryRepository)
    }

    @Test
    internal fun `should do no release when no factory is found`() = testDispatcherProvider.run {
        // when
        clusterFactoryService.releaseFactories(mockk(), emptyList())

        // then
        confirmVerified(factoryRepository, campaignRepository, campaignFactoryRepository)
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