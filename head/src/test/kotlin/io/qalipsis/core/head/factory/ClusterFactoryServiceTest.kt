package io.qalipsis.core.head.factory

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphEntity
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphTagEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.head.jdbc.entity.FactoryTagEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignFactoryRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphRepository
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphSelectorRepository
import io.qalipsis.core.head.jdbc.repository.FactoryRepository
import io.qalipsis.core.head.jdbc.repository.FactoryStateRepository
import io.qalipsis.core.head.jdbc.repository.FactoryTagRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * @author rklymenko
 */
@WithMockk
internal class ClusterFactoryServiceTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryRepository: FactoryRepository

    @RelaxedMockK
    private lateinit var factoryTagRepository: FactoryTagRepository

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

    @RelaxedMockK
    private lateinit var tenantRepository: TenantRepository

    @InjectMockKs
    private lateinit var clusterFactoryService: ClusterFactoryService

    @Test
    fun `should register new factory without tags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val handshakeRequest =
            HandshakeRequest(
                nodeId = "testNodeId",
                tags = emptyMap(),
                replyTo = "",
                scenarios = emptyList(),
                tenant = "my-tenant",
                zone = "fr"
            )
        val now = getTimeMock()
        coEvery { factoryRepository.findByNodeIdIn(any(), listOf(actualNodeId)) } returns emptyList()
        coEvery { factoryRepository.save(any()) } returns relaxedMockk { every { id } returns 123 }
        val handshakeResponse = relaxedMockk<HandshakeResponse> {
            every { unicastChannel } returns "directives-unicast-boo"
        }


        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveFactory",
            5243L,
            actualNodeId,
            handshakeRequest,
            handshakeResponse
        )

        //then
        coVerifyOrder {
            factoryRepository.findByNodeIdIn("my-tenant", listOf(actualNodeId))
            factoryRepository.save(
                FactoryEntity(
                    nodeId = actualNodeId,
                    registrationTimestamp = now,
                    registrationNodeId = handshakeRequest.nodeId,
                    unicastChannel = "directives-unicast-boo",
                    tenantId = 5243L,
                    zone = handshakeRequest.zone
                )
            )
            factoryStateRepository.save(FactoryStateEntity(now, 123, now, 0, FactoryStateValue.REGISTERED))
        }
        confirmVerified(factoryRepository, factoryStateRepository)
    }

    @Test
    fun `should register new factory with tags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            tags = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = emptyList(),
            tenant = "my-tenant",
            zone = "fr"
        )
        val handshakeResponse = relaxedMockk<HandshakeResponse> {
            every { unicastChannel } returns "directives-unicast-boo"
        }
        val now = getTimeMock()
        coEvery { factoryRepository.findByNodeIdIn(any(), listOf(actualNodeId)) } returns emptyList()
        coEvery { factoryRepository.save(any()) } returns relaxedMockk { every { id } returns 123 }

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveFactory",
            123L,
            actualNodeId,
            handshakeRequest,
            handshakeResponse
        )

        //then
        coVerifyOrder {
            factoryRepository.findByNodeIdIn("my-tenant", listOf(actualNodeId))
            factoryRepository.save(
                FactoryEntity(
                    nodeId = actualNodeId,
                    registrationTimestamp = now,
                    registrationNodeId = handshakeRequest.nodeId,
                    unicastChannel = "directives-unicast-boo",
                    tenantId = 123,
                    zone = handshakeRequest.zone
                )
            )
            factoryTagRepository.saveAll(listOf(FactoryTagEntity(123, selectorKey, selectorValue)))
            factoryStateRepository.save(FactoryStateEntity(now, 123, now, 0, FactoryStateValue.REGISTERED))
        }
        confirmVerified(factoryRepository, factoryTagRepository)
    }

    @Test
    fun `should update existing factory without tags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val handshakeRequest =
            HandshakeRequest(
                nodeId = "testNodeId",
                tags = emptyMap(),
                replyTo = "",
                scenarios = emptyList(),
                tenant = "my-tenant"
            )
        val now = getTimeMock()
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast",
            tenantId = 1276L
        )
        val handshakeResponse = relaxedMockk<HandshakeResponse> {
            every { unicastChannel } returns "directives-unicast-boo"
        }

        val savedFactoryEntity = slot<FactoryEntity>()
        coEvery { factoryRepository.save(capture(savedFactoryEntity)) } returnsArgument 0
        coEvery { factoryRepository.findByNodeIdIn(any(), listOf(actualNodeId)) } returns listOf(factoryEntity)

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveFactory",
            15438L,
            actualNodeId,
            handshakeRequest,
            handshakeResponse
        )

        //then
        assertThat(savedFactoryEntity.captured).all {
            prop(FactoryEntity::nodeId).isEqualTo(actualNodeId)
            prop(FactoryEntity::registrationNodeId).isEqualTo(handshakeRequest.nodeId)
            prop(FactoryEntity::registrationTimestamp).isEqualTo(now)
            prop(FactoryEntity::unicastChannel).isEqualTo("directives-unicast-boo")
            prop(FactoryEntity::tenantId).isEqualTo(1276L)
        }
        coVerifyOrder {
            factoryRepository.findByNodeIdIn("my-tenant", listOf(actualNodeId))
            factoryRepository.save(any())
        }
        coVerifyNever {
            factoryTagRepository.saveAll(any<Iterable<FactoryTagEntity>>())
            factoryTagRepository.updateAll(any<Iterable<FactoryTagEntity>>())
            factoryTagRepository.deleteAll(any())
        }
        confirmVerified(factoryRepository, factoryTagRepository)
    }

    @Test
    fun `should save new tags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            tags = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = emptyList()
        )
        val now = getTimeMock()
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast",
            tags = listOf()
        )
        val newSelector = FactoryTagEntity(factoryEntity.id, selectorKey, selectorValue)

        coEvery { factoryTagRepository.saveAll(listOf(newSelector)) } returns flowOf(newSelector)

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "mergeTags",
            factoryTagRepository,
            handshakeRequest.tags,
            factoryEntity.tags,
            factoryEntity.id
        )

        //then
        val expectedSelector = FactoryTagEntity(factoryEntity.id, selectorKey, selectorValue)
        coVerifyOnce {
            factoryTagRepository.saveAll(listOf(expectedSelector))
        }
        coVerifyNever {
            factoryTagRepository.updateAll(any<Iterable<FactoryTagEntity>>())
            factoryTagRepository.deleteAll(any())
        }
        confirmVerified(factoryTagRepository)
    }

    @Test
    fun `should update existing tags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"
        val selectorNewValue = "test-selector-new-value"
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            tags = mapOf(selectorKey to selectorNewValue),
            replyTo = "",
            scenarios = emptyList()
        )
        val now = getTimeMock()
        val selector = FactoryTagEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast",
            tags = listOf(selector)
        )

        coEvery { factoryTagRepository.updateAll(listOf(selector)) } returns flowOf(selector)

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "mergeTags",
            factoryTagRepository,
            handshakeRequest.tags,
            factoryEntity.tags,
            factoryEntity.id
        )

        //then
        val expectedSelector = FactoryTagEntity(factoryEntity.id, selectorKey, selectorNewValue)
        coVerifyOnce {
            factoryTagRepository.updateAll(listOf(expectedSelector))
        }
        coVerifyNever {
            factoryTagRepository.saveAll(any<Iterable<FactoryTagEntity>>())
            factoryTagRepository.deleteAll(any())
        }
        confirmVerified(factoryTagRepository)
    }

    @Test
    fun `should delete existing tags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"
        val handshakeRequest =
            HandshakeRequest(nodeId = "testNodeId", tags = emptyMap(), replyTo = "", scenarios = emptyList())
        val now = getTimeMock()
        val selector = FactoryTagEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast",
            tags = listOf(selector)
        )

        coEvery { factoryTagRepository.deleteAll(listOf(selector)) } returns 1

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "mergeTags",
            factoryTagRepository,
            handshakeRequest.tags,
            factoryEntity.tags,
            factoryEntity.id
        )

        //then
        coVerifyOnce {
            factoryTagRepository.deleteAll(listOf(selector))
        }
        coVerifyNever {
            factoryTagRepository.updateAll(any<Iterable<FactoryTagEntity>>())
            factoryTagRepository.saveAll(any<Iterable<FactoryTagEntity>>())
        }
        confirmVerified(factoryTagRepository)
    }

    @Test
    fun `should skip update of existing tags because they were not changed`() =
        testDispatcherProvider.run {
            //given
            val actualNodeId = "boo"
            val selectorKey = "test-selector-key"
            val selectorValue = "test-selector-value"
            val handshakeRequest = HandshakeRequest(
                nodeId = "testNodeId",
                tags = mapOf(selectorKey to selectorValue),
                replyTo = "",
                scenarios = emptyList()
            )
            val now = getTimeMock()
            val selector = FactoryTagEntity(-1, selectorKey, selectorValue)
            val factoryEntity = FactoryEntity(
                nodeId = actualNodeId,
                registrationTimestamp = now,
                registrationNodeId = handshakeRequest.nodeId,
                unicastChannel = "unicast",
                tags = listOf(selector)
            )

            // when
            clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
                "mergeTags",
                factoryTagRepository,
                handshakeRequest.tags,
                factoryEntity.tags,
                factoryEntity.id
            )

            //then
            coVerifyNever {
                factoryTagRepository.updateAll(any<Iterable<FactoryTagEntity>>())
            }
            confirmVerified(factoryTagRepository)
        }

    @Test
    fun `should create new tags and update existing tags and delete old tags and dont touch unchanged tags`() =
        testDispatcherProvider.run {
            //given
            val actualNodeId = "boo"
            val existingTagsMap =
                mapOf("test0key" to "test0value", "test2key" to "test002value", "test4key" to "test4value")
            val newTagsMap =
                mapOf("test1key" to "test1value", "test2key" to "test2value", "test4key" to "test4value")
            val handshakeRequest = HandshakeRequest(
                nodeId = "testNodeId",
                tags = newTagsMap,
                replyTo = "",
                scenarios = emptyList()
            )
            val now = getTimeMock()
            val existingTags = existingTagsMap.map { FactoryTagEntity(-1, it.key, it.value) }
            val factoryEntity = FactoryEntity(
                nodeId = actualNodeId,
                registrationTimestamp = now,
                registrationNodeId = handshakeRequest.nodeId,
                unicastChannel = "unicast",
                tags = existingTags
            )

            coEvery { factoryTagRepository.deleteAll(listOf(existingTags[0])) } returns 1
            coEvery {
                factoryTagRepository.updateAll(
                    listOf(
                        FactoryTagEntity(
                            factoryEntity.id,
                            "test2key",
                            newTagsMap["test2key"]!!
                        )
                    )
                )
            } returns flowOf()
            coEvery {
                factoryTagRepository.saveAll(
                    listOf(
                        FactoryTagEntity(
                            factoryEntity.id,
                            "test1key",
                            "test1value"
                        )
                    )
                )
            } returns flowOf()

            // when
            clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
                "mergeTags",
                factoryTagRepository,
                handshakeRequest.tags,
                factoryEntity.tags,
                factoryEntity.id
            )

            //then
            coVerifyOrder {
                factoryTagRepository.deleteAll(listOf(existingTags[0]))
                factoryTagRepository.updateAll(
                    listOf(
                        FactoryTagEntity(
                            factoryEntity.id,
                            "test2key",
                            newTagsMap["test2key"]!!
                        )
                    )
                )
                factoryTagRepository.saveAll(
                    listOf(
                        FactoryTagEntity(
                            factoryEntity.id,
                            "test1key",
                            "test1value"
                        )
                    )
                )
            }
            confirmVerified(factoryTagRepository)
        }

    @Test
    fun `should create new scenario and dags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"

        val graphSummary = DirectedAcyclicGraphSummary(name = "new-test-dag-id")
        val newRegistrationScenario = RegistrationScenario(
            name = "new-test-scenario",
            minionsCount = 1,
            directedAcyclicGraphs = listOf(graphSummary)
        )
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            tags = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario)
        )
        val now = getTimeMock()
        val selector = FactoryTagEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast",
            tags = listOf(selector),
            tenantId = 1
        )
        val dag = DirectedAcyclicGraphSummary(name = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag, name = newRegistrationScenario.name)

        coEvery { factoryRepository.findByNodeIdIn(any(), listOf(actualNodeId)) } returns listOf(factoryEntity)
        coEvery { scenarioRepository.findByFactoryId(any(), factoryEntity.id) } returns emptyList()
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.deleteAll(any()) } returns 1
        coEvery { scenarioRepository.saveAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)
        coEvery { directedAcyclicGraphRepository.deleteAll(any()) } returns 1
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf()

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveScenariosAndDependencies",
            "my-tenant",
            handshakeRequest.scenarios,
            factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId("my-tenant", factoryEntity.id)
            scenarioRepository.saveAll(any<Iterable<ScenarioEntity>>())
            directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>())
        }
        confirmVerified(
            tenantRepository,
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

        val graphSummary = DirectedAcyclicGraphSummary(name = "new-test-dag-id")
        val newRegistrationScenario = RegistrationScenario(
            name = "test",
            minionsCount = 1,
            directedAcyclicGraphs = listOf(graphSummary)
        )
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            tags = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario)
        )
        val now = getTimeMock()
        val selector = FactoryTagEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast",
            tags = listOf(selector),
            tenantId = 1
        )
        val dag = DirectedAcyclicGraphSummary(name = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag)

        coEvery { scenarioRepository.findByFactoryId(any(), factoryEntity.id) } returns listOf(scenarioEntity)
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)
        coEvery { directedAcyclicGraphRepository.deleteAll(any()) } returns 1
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf()

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveScenariosAndDependencies",
            "my-tenant",
            handshakeRequest.scenarios,
            factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId("my-tenant", factoryEntity.id)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id))
            scenarioRepository.updateAll(listOf(scenarioEntity.copy(enabled = true)))
            directedAcyclicGraphRepository.saveAll(handshakeRequest.scenarios.flatMap { it.directedAcyclicGraphs }
                .map { dag ->
                    DirectedAcyclicGraphEntity(
                        scenarioId = 1,
                        name = dag.name,
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
    fun `should update scenario and dags and dag tags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"

        val graphSummary = DirectedAcyclicGraphSummary(
            name = "new-test-dag-id",
            tags = mapOf("test_dag_selector1" to "test_dag_selector_value1")
        )
        val newRegistrationScenario = RegistrationScenario(
            name = "test",
            minionsCount = 1,
            directedAcyclicGraphs = listOf(graphSummary)
        )
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            tags = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario)
        )
        val now = getTimeMock()
        val selector = FactoryTagEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast",
            tags = listOf(selector),
            tenantId = 1
        )
        val dag = DirectedAcyclicGraphSummary(name = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag)
        val savedDag = DirectedAcyclicGraphEntity(
            scenarioId = scenarioEntity.id,
            name = graphSummary.name,
            isRoot = graphSummary.isRoot,
            singleton = true,
            underLoad = true,
            numberOfSteps = 1,
            version = now,
            tags = graphSummary.tags.map { DirectedAcyclicGraphTagEntity(1, it.key, it.value) })

        coEvery { scenarioRepository.findByFactoryId(any(), factoryEntity.id) } returns listOf(scenarioEntity)
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
            "my-tenant",
            handshakeRequest.scenarios,
            factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId("my-tenant", factoryEntity.id)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id))
            directedAcyclicGraphSelectorRepository.deleteByDirectedAcyclicGraphIdIn(scenarioEntity.dags.map { it.id })
            scenarioRepository.updateAll(listOf(scenarioEntity.copy(enabled = true)))
            directedAcyclicGraphRepository.saveAll(handshakeRequest.scenarios.flatMap { it.directedAcyclicGraphs }
                .map { dag ->
                    DirectedAcyclicGraphEntity(
                        scenarioId = 1,
                        name = dag.name,
                        singleton = dag.isSingleton,
                        underLoad = dag.isUnderLoad,
                        numberOfSteps = dag.numberOfSteps,
                        version = now,
                        isRoot = false
                    )
                })
            directedAcyclicGraphSelectorRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphTagEntity(
                        id = -1,
                        directedAcyclicGraphId = -1,
                        key = "test_dag_selector1",
                        value = graphSummary.tags["test_dag_selector1"]!!
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
    fun `should save dags and dag tags`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val graphSummary = DirectedAcyclicGraphSummary(
            name = "new-test-dag-id",
            tags = mapOf("test_dag_selector1" to "test_dag_selector_value1")
        )
        val directedAcyclicGraphs = listOf(graphSummary)
        val savedDag = DirectedAcyclicGraphEntity(
            scenarioId = 1,
            name = graphSummary.name,
            isRoot = false,
            singleton = true,
            underLoad = true,
            numberOfSteps = 1,
            version = now,
            tags = graphSummary.tags.map { DirectedAcyclicGraphTagEntity(1, it.key, it.value) })
        val expectedDagEntity = DirectedAcyclicGraphEntity(
            scenarioId = 1,
            name = "new-test-dag-id",
            isRoot = false,
            singleton = true,
            underLoad = true,
            numberOfSteps = 1,
            version = now,
            tags = listOf(
                DirectedAcyclicGraphTagEntity(
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
            "saveDagsAndTags",
            listOf(savedDag),
            directedAcyclicGraphs
        )

        //then
        coVerifyOrder {

            directedAcyclicGraphRepository.saveAll(listOf(expectedDagEntity))
            directedAcyclicGraphSelectorRepository.saveAll(expectedDagEntity.tags.map {
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
    fun `should save dags without dag tags`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val graphSummary = DirectedAcyclicGraphSummary(name = "new-test-dag-id")
        val directedAcyclicGraphs = listOf(graphSummary)
        val savedDag = DirectedAcyclicGraphEntity(
            scenarioId = 1,
            name = graphSummary.name,
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
            tags = emptyList()
        )

        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf(
            savedDag
        )

        // when
        clusterFactoryService.coInvokeInvisible<ClusterFactoryService>(
            "saveDagsAndTags",
            listOf(savedDag),
            directedAcyclicGraphs
        )

        //then
        coVerifyOrder {
            directedAcyclicGraphRepository.saveAll(listOf(expectedDagEntity))
        }
        coVerifyNever {
            directedAcyclicGraphSelectorRepository.saveAll(any<Iterable<DirectedAcyclicGraphTagEntity>>())
        }
        confirmVerified(
            directedAcyclicGraphRepository,
            directedAcyclicGraphSelectorRepository
        )
    }

    @Test
    fun `should update existing channels, scenario and dags`() = testDispatcherProvider.run {
        //given
        val actualNodeId = "boo"
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"

        val graphSummary = DirectedAcyclicGraphSummary(name = "new-test-dag-id")
        val newRegistrationScenario =
            RegistrationScenario(name = "test", minionsCount = 1, directedAcyclicGraphs = listOf(graphSummary))
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            tags = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario),
            tenant = "my-tenant"
        )
        val handshakeResponse = HandshakeResponse(
            handshakeNodeId = "testNodeId",
            nodeId = actualNodeId,
            unicastChannel = "directives-unicast",
            heartbeatChannel = "heartbeat",
            heartbeatPeriod = Duration.ofMinutes(1)
        )
        val now = getTimeMock()
        val selector = FactoryTagEntity(-1, selectorKey, selectorValue)
        val factoryEntity = FactoryEntity(
            tenantId = 321,
            nodeId = actualNodeId,
            registrationTimestamp = now,
            registrationNodeId = handshakeRequest.nodeId,
            unicastChannel = "unicast-before",
            tags = listOf(selector),
            zone = "ru"
        )

        val dag = DirectedAcyclicGraphSummary(name = "test", isSingleton = true, isUnderLoad = true)
        val scenarioEntity = createScenario(now, factoryEntity.id, dag, true)

        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 123
        coEvery { factoryRepository.findByNodeIdIn(any(), listOf(actualNodeId)) } returns listOf(factoryEntity)
        coEvery { scenarioRepository.findByFactoryId(any(), factoryEntity.id) } returns listOf(scenarioEntity)
        coEvery { factoryStateRepository.save(any()) } returnsArgument 0
        coEvery { factoryRepository.save(any()) } returnsArgument 0
        val savedFactoryEntity = slot<FactoryEntity>()
        coEvery { factoryRepository.save(capture(savedFactoryEntity)) } returnsArgument 0
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id)) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(scenarioEntity)

        // when
        clusterFactoryService.register(actualNodeId, handshakeRequest, handshakeResponse)

        //then
        assertThat(savedFactoryEntity.captured).all {
            prop(FactoryEntity::nodeId).isEqualTo(actualNodeId)
            prop(FactoryEntity::registrationNodeId).isEqualTo(handshakeRequest.nodeId)
            prop(FactoryEntity::registrationTimestamp).isEqualTo(now)
            prop(FactoryEntity::unicastChannel).isEqualTo("directives-unicast")
            prop(FactoryEntity::tenantId).isEqualTo(321)
            prop(FactoryEntity::zone).isEqualTo("ru")
        }
        coVerifyOrder {
            tenantRepository.findIdByReference("my-tenant")
            factoryRepository.findByNodeIdIn("my-tenant", listOf(actualNodeId))
            factoryRepository.save(any())
            factoryStateRepository.save(any())
            scenarioRepository.findByFactoryId("my-tenant", factoryEntity.id)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(scenarioEntity.id))
            scenarioRepository.updateAll(listOf(scenarioEntity))
            directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>())
        }
        confirmVerified(
            factoryRepository,
            tenantRepository,
            scenarioRepository,
            factoryStateRepository,
            directedAcyclicGraphRepository
        )
    }

    @Test
    fun `should return all active scenarios`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val dag = DirectedAcyclicGraphSummary(name = "test", isSingleton = true, isUnderLoad = true)
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
                        dag.name,
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
        coEvery { scenarioRepository.findActiveByName(any(), ids) } returns scenarioEntities


        //when
        val scenarios = clusterFactoryService.getActiveScenarios("", ids)

        //then
        coVerifyOnce {
            scenarioRepository.findActiveByName(any(), ids)
        }

        assertEquals(scenarioEntities.map { scenario ->
            ScenarioSummary(
                name = scenario.name,
                minionsCount = scenario.defaultMinionsCount,
                directedAcyclicGraphs = scenario.dags.map { dag ->
                    DirectedAcyclicGraphSummary(
                        name = dag.name,
                        isSingleton = dag.singleton,
                        isRoot = false,
                        isUnderLoad = dag.underLoad,
                        numberOfSteps = dag.numberOfSteps,
                        tags = emptyMap()
                    )
                })
        }, scenarios)

        confirmVerified(scenarioRepository)
    }

    @Test
    fun `should return all active scenarios with sorting`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val dag = DirectedAcyclicGraphSummary(name = "test", isSingleton = true, isUnderLoad = true)
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
                        dag.name,
                        dag.isRoot,
                        dag.isSingleton,
                        dag.isUnderLoad,
                        dag.numberOfSteps,
                        emptyList()
                    )
                )
            )
        )
        coEvery { scenarioRepository.findAllActiveWithSorting("my-tenant", "name") } returns scenarioEntities


        //when
        val scenarios = clusterFactoryService.getAllActiveScenarios("my-tenant", "name")

        //then
        coVerifyOnce {
            scenarioRepository.findAllActiveWithSorting("my-tenant", "name")
        }

        assertEquals(scenarioEntities.map { scenario ->
            ScenarioSummary(
                name = scenario.name,
                minionsCount = scenario.defaultMinionsCount,
                directedAcyclicGraphs = scenario.dags.map { dag ->
                    DirectedAcyclicGraphSummary(
                        name = dag.name,
                        isSingleton = dag.singleton,
                        isRoot = false,
                        isUnderLoad = dag.underLoad,
                        numberOfSteps = dag.numberOfSteps,
                        tags = emptyMap()
                    )
                })
        }, scenarios)

        confirmVerified(scenarioRepository)
    }

    @Test
    fun `should return all active scenarios with sorting desc`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val dag = DirectedAcyclicGraphSummary(name = "test", isSingleton = true, isUnderLoad = true)
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
                        dag.name,
                        dag.isRoot,
                        dag.isSingleton,
                        dag.isUnderLoad,
                        dag.numberOfSteps,
                        emptyList()
                    )
                )
            ),
            ScenarioEntity(
                id = 2,
                version = now,
                factoryId = 2,
                name = "test2",
                defaultMinionsCount = 2,
                enabled = true,
                dags = listOf(
                    DirectedAcyclicGraphEntity(
                        -1,
                        dag.name,
                        dag.isRoot,
                        dag.isSingleton,
                        dag.isUnderLoad,
                        dag.numberOfSteps,
                        emptyList()
                    )
                )
            )
        )
        coEvery { scenarioRepository.findAllActiveWithSorting("my-tenant", "name") } returns scenarioEntities


        //when
        val scenarios2 = clusterFactoryService.getAllActiveScenarios("my-tenant", "name:desc")

        //then
        coVerifyOnce {
            scenarioRepository.findAllActiveWithSorting("my-tenant", "name")
        }

        assertEquals(scenarioEntities.map { scenario ->
            ScenarioSummary(
                name = scenario.name,
                minionsCount = scenario.defaultMinionsCount,
                directedAcyclicGraphs = scenario.dags.map { dag ->
                    DirectedAcyclicGraphSummary(
                        name = dag.name,
                        isSingleton = dag.singleton,
                        isRoot = false,
                        isUnderLoad = dag.underLoad,
                        numberOfSteps = dag.numberOfSteps,
                        tags = emptyMap()
                    )
                })
        }.reversed(), scenarios2)

        confirmVerified(scenarioRepository)
    }

    @Test
    fun `should return all active scenarios with sorting asc`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()

        val dag = DirectedAcyclicGraphSummary(name = "test", isSingleton = true, isUnderLoad = true)
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
                        dag.name,
                        dag.isRoot,
                        dag.isSingleton,
                        dag.isUnderLoad,
                        dag.numberOfSteps,
                        emptyList()
                    )
                )
            ),
            ScenarioEntity(
                id = 2,
                version = now,
                factoryId = 2,
                name = "test2",
                defaultMinionsCount = 2,
                enabled = true,
                dags = listOf(
                    DirectedAcyclicGraphEntity(
                        -1,
                        dag.name,
                        dag.isRoot,
                        dag.isSingleton,
                        dag.isUnderLoad,
                        dag.numberOfSteps,
                        emptyList()
                    )
                )
            )
        )
        coEvery { scenarioRepository.findAllActiveWithSorting("my-tenant", "name") } returns scenarioEntities


        //when
        val scenarios2 = clusterFactoryService.getAllActiveScenarios("my-tenant", "name:asc")

        //then
        coVerifyOnce {
            scenarioRepository.findAllActiveWithSorting("my-tenant", "name")
        }

        assertEquals(scenarioEntities.map { scenario ->
            ScenarioSummary(
                name = scenario.name,
                minionsCount = scenario.defaultMinionsCount,
                directedAcyclicGraphs = scenario.dags.map { dag ->
                    DirectedAcyclicGraphSummary(
                        name = dag.name,
                        isSingleton = dag.singleton,
                        isRoot = false,
                        isUnderLoad = dag.underLoad,
                        numberOfSteps = dag.numberOfSteps,
                        tags = emptyMap()
                    )
                })
        }, scenarios2)

        confirmVerified(scenarioRepository)
    }

    @Test
    fun `should update heartbeat`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()
        val factoryId = 1L
        val heartbeat =
            Heartbeat(nodeId = "boo", timestamp = now, state = Heartbeat.State.UNREGISTERED, campaignKey = "1")

        coEvery { factoryRepository.findIdByNodeIdIn(listOf(heartbeat.nodeId)) } returns listOf(factoryId)

        //when
        clusterFactoryService.notify(heartbeat)

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
        coEvery { scenarioRepository.findActiveByName(any(), refEq(scenarios)) } returns listOf(
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
        coEvery { factoryRepository.getAvailableFactoriesForScenarios(any(), refEq(scenarios)) } returns listOf(
            factoryEntity1, factoryEntity2
        )

        // when
        val result = clusterFactoryService.getAvailableFactoriesForScenarios("campaign.tenant", scenarios)

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
        coEvery { campaignRepository.findIdByTenantAndKeyAndEndIsNull("my-tenant", "my-campaign") } returns 765
        val runningCampaign = mockk<RunningCampaign> {
            every { key } returns "my-campaign"
            every { tenant } returns "my-tenant"
        }

        // when
        clusterFactoryService.lockFactories(runningCampaign, factories)

        // then
        coVerifyOrder {
            factoryRepository.findIdByNodeIdIn(refEq(factories))
            campaignRepository.findIdByTenantAndKeyAndEndIsNull("my-tenant", "my-campaign")
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
        val runningCampaign = mockk<RunningCampaign> { every { key } returns "my-campaign" }

        // when
        clusterFactoryService.lockFactories(runningCampaign, factories)

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
        coEvery { campaignRepository.findIdByTenantAndKey("my-tenant", "my-campaign") } returns 765
        val runningCampaign = mockk<RunningCampaign> {
            every { key } returns "my-campaign"
            every { tenant } returns "my-tenant"
        }

        // when
        clusterFactoryService.releaseFactories(runningCampaign, factories)

        // then
        coVerifyOrder {
            factoryRepository.findIdByNodeIdIn(refEq(factories))
            campaignRepository.findIdByTenantAndKey("my-tenant", "my-campaign")
            campaignFactoryRepository.discard(765, listOf(12, 32))
        }
        confirmVerified(factoryRepository, campaignRepository, campaignFactoryRepository)
    }

    @Test
    internal fun `should do no release when no factory is provided`() = testDispatcherProvider.run {
        // given
        val factories = listOf("factory-1", "factory-2")
        coEvery { factoryRepository.findIdByNodeIdIn(refEq(factories)) } returns emptyList()
        val runningCampaign = mockk<RunningCampaign> { every { key } returns "my-campaign" }

        // when
        clusterFactoryService.releaseFactories(runningCampaign, factories)

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
                    dag.name,
                    dag.isRoot,
                    dag.isSingleton,
                    dag.isUnderLoad,
                    dag.numberOfSteps,
                    dag.tags.map { (key, value) -> DirectedAcyclicGraphTagEntity(-1, key, value) })
            )
        )
}