/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphEntity
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphTagEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphRepository
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphTagRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioRepository
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@WithMockk
internal class ScenarioDetailsUpdaterImplTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var scenarioRepository: ScenarioRepository

    @MockK
    private lateinit var directedAcyclicGraphRepository: DirectedAcyclicGraphRepository

    @MockK
    private lateinit var directedAcyclicGraphTagRepository: DirectedAcyclicGraphTagRepository

    @InjectMockKs
    private lateinit var scenarioDetailsUpdaterImpl: ScenarioDetailsUpdaterImpl

    @AfterAll
    fun tearDownAll() {
        unmockkStatic(Clock::class)
    }

    @Test
    fun `should create new scenario and dags`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()
        val dag =
            DirectedAcyclicGraphSummary(name = "new-test-dag-id", isUnderLoad = true, numberOfSteps = 5, isRoot = true)
        val scenarioSummary = ScenarioSummary(
            name = "new-test-scenario",
            version = "0.1",
            builtAt = now.minusSeconds(5),
            minionsCount = 1,
            directedAcyclicGraphs = listOf(dag)
        )
        val factoryEntity = mockk<FactoryEntity> {
            every { id } returns 4
        }
        coEvery { scenarioRepository.findByFactoryId(any(), any()) } returns emptyList()
        coEvery { scenarioRepository.saveAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(mockk {
            every { id } returns 6165
            every { name } returns "new-test-scenario"
        })
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf()

        // when
        scenarioDetailsUpdaterImpl.saveOrUpdateScenarios(
            tenantReference = "my-tenant",
            registrationScenarios = listOf(scenarioSummary),
            existingFactory = factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId("my-tenant", 4)
            scenarioRepository.saveAll(
                listOf(
                    ScenarioEntity(
                        id = -1,
                        version = now,
                        factoryId = 4,
                        name = "new-test-scenario",
                        scenarioVersion = "0.1",
                        builtAt = now.minusSeconds(5),
                        defaultMinionsCount = 1,
                        enabled = true,
                        dags = emptyList()
                    )
                )
            )
            directedAcyclicGraphRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphEntity(
                        id = -1,
                        version = now,
                        scenarioId = 6165,
                        name = "new-test-dag-id",
                        root = true,
                        singleton = false,
                        underLoad = true,
                        numberOfSteps = 5,
                        tags = emptyList()
                    )
                )
            )
        }
        confirmVerified(
            scenarioRepository,
            directedAcyclicGraphRepository,
            directedAcyclicGraphTagRepository
        )
    }

    @Test
    fun `should update scenario and dags and delete the non-provided scenarios`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()
        val dag =
            DirectedAcyclicGraphSummary(name = "test-dag", isUnderLoad = true, numberOfSteps = 5, isRoot = true)
        val scenarioSummary = ScenarioSummary(
            name = "test",
            version = "0.1",
            builtAt = now.minusSeconds(5),
            minionsCount = 1,
            directedAcyclicGraphs = listOf(dag)
        )
        val factoryEntity = mockk<FactoryEntity> {
            every { id } returns 543
        }
        val existingDag = DirectedAcyclicGraphSummary(name = "test-dag", isSingleton = true, isUnderLoad = true)
        val existingScenarioEntityToUpdate = createScenario(now, factoryEntity.id, existingDag).copy(id = 54125)
        val existingScenarioEntityToDelete =
            createScenario(now, factoryEntity.id, existingDag).copy(id = 453, name = "test-other")

        coEvery { scenarioRepository.findByFactoryId(any(), any()) } returns listOf(
            existingScenarioEntityToUpdate,
            existingScenarioEntityToDelete
        )
        coEvery { scenarioRepository.deleteAll(any<Iterable<ScenarioEntity>>()) } returns 1
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(any()) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(
            existingScenarioEntityToUpdate
        )
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf()
        coEvery { directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(any()) } returns 1

        // when
        scenarioDetailsUpdaterImpl.saveOrUpdateScenarios(
            tenantReference = "my-tenant",
            registrationScenarios = listOf(scenarioSummary),
            existingFactory = factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId("my-tenant", 543)
            scenarioRepository.deleteAll(listOf(existingScenarioEntityToDelete))
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(54125))
            directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(listOf(235))
            scenarioRepository.updateAll(
                listOf(
                    existingScenarioEntityToUpdate.copy(
                        enabled = true,
                        dags = emptyList()
                    )
                )
            )
            directedAcyclicGraphRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphEntity(
                        id = -1,
                        version = now,
                        scenarioId = 54125,
                        name = "test-dag",
                        root = true,
                        singleton = false,
                        underLoad = true,
                        numberOfSteps = 5,
                        tags = emptyList()
                    )
                )
            )
        }
        confirmVerified(
            scenarioRepository,
            directedAcyclicGraphRepository,
            directedAcyclicGraphTagRepository
        )
    }

    @Test
    fun `should update scenario and dags but keep the non-provided scenarios`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()
        val dag =
            DirectedAcyclicGraphSummary(name = "test-dag", isUnderLoad = true, numberOfSteps = 5, isRoot = true)
        val scenarioSummary = ScenarioSummary(
            name = "test",
            version = "0.1",
            builtAt = now.minusSeconds(5),
            minionsCount = 1,
            directedAcyclicGraphs = listOf(dag)
        )
        val factoryEntity = mockk<FactoryEntity> {
            every { id } returns 543
        }
        val existingDag = DirectedAcyclicGraphSummary(name = "test-dag", isSingleton = true, isUnderLoad = true)
        val existingScenarioEntityToUpdate = createScenario(now, factoryEntity.id, existingDag).copy(id = 54125)
        val existingScenarioEntityToKeepUntouched =
            createScenario(now, factoryEntity.id, existingDag).copy(id = 453, name = "test-other")

        coEvery { scenarioRepository.findByFactoryId(any(), any()) } returns listOf(
            existingScenarioEntityToUpdate,
            existingScenarioEntityToKeepUntouched
        )
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(any()) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(
            existingScenarioEntityToUpdate
        )
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf()
        coEvery { directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(any()) } returns 1

        // when
        scenarioDetailsUpdaterImpl.saveOrUpdateScenarios(
            tenantReference = "my-tenant",
            registrationScenarios = listOf(scenarioSummary),
            existingFactory = factoryEntity,
            deleteAbsentScenarios = false
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId("my-tenant", 543)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(54125))
            directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(listOf(235))
            scenarioRepository.updateAll(
                listOf(
                    existingScenarioEntityToUpdate.copy(
                        enabled = true,
                        dags = emptyList()
                    )
                )
            )
            directedAcyclicGraphRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphEntity(
                        id = -1,
                        version = now,
                        scenarioId = 54125,
                        name = "test-dag",
                        root = true,
                        singleton = false,
                        underLoad = true,
                        numberOfSteps = 5,
                        tags = emptyList()
                    )
                )
            )
        }
        confirmVerified(
            scenarioRepository,
            directedAcyclicGraphRepository,
            directedAcyclicGraphTagRepository
        )
    }

    @Test
    fun `should update scenario without dags`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()
        val scenarioSummary = ScenarioSummary(
            name = "test",
            version = "0.1",
            builtAt = now.minusSeconds(5),
            minionsCount = 1,
            directedAcyclicGraphs = emptyList()
        )
        val factoryEntity = mockk<FactoryEntity> {
            every { id } returns 543
        }
        val existingDag = DirectedAcyclicGraphSummary(name = "test-dag", isSingleton = true, isUnderLoad = true)
        val existingScenarioEntity = createScenario(now, factoryEntity.id, existingDag).copy(id = 54125)

        coEvery { scenarioRepository.findByFactoryId(any(), any()) } returns listOf(existingScenarioEntity)
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(any()) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(existingScenarioEntity)
        coEvery { directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(any()) } returns 1

        // when
        scenarioDetailsUpdaterImpl.saveOrUpdateScenarios(
            "my-tenant",
            listOf(scenarioSummary),
            factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId("my-tenant", 543)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(54125))
            directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(listOf(235))
            scenarioRepository.updateAll(listOf(existingScenarioEntity.copy(enabled = true, dags = emptyList())))
        }
        confirmVerified(
            scenarioRepository,
            directedAcyclicGraphRepository,
            directedAcyclicGraphTagRepository
        )
    }

    @Test
    fun `should update scenario and dags and dag tags`() = testDispatcherProvider.run {
        //given
        val now = getTimeMock()
        val dag =
            DirectedAcyclicGraphSummary(
                name = "test-dag", isUnderLoad = true, numberOfSteps = 5, isRoot = true,
                tags = mapOf("test_dag_tag1" to "test_dag_tag_value1")
            )
        val scenarioSummary = ScenarioSummary(
            name = "test",
            version = "0.1",
            builtAt = now.minusSeconds(5),
            minionsCount = 1,
            directedAcyclicGraphs = listOf(dag)
        )
        val factoryEntity = mockk<FactoryEntity> {
            every { id } returns 543
        }
        val existingDag = DirectedAcyclicGraphSummary(name = "test-dag", isSingleton = true, isUnderLoad = true)
        val existingScenarioEntity = createScenario(now, factoryEntity.id, existingDag).copy(id = 54125)

        coEvery { scenarioRepository.findByFactoryId(any(), any()) } returns listOf(existingScenarioEntity)
        coEvery { directedAcyclicGraphRepository.deleteByScenarioIdIn(any()) } returns 1
        coEvery { scenarioRepository.updateAll(any<Iterable<ScenarioEntity>>()) } returns flowOf(existingScenarioEntity)
        coEvery { directedAcyclicGraphRepository.saveAll(any<Iterable<DirectedAcyclicGraphEntity>>()) } returns flowOf(
            mockk {
                every { id } returns 38671
                every { name } returns "test-dag"
            })
        coEvery { directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(any()) } returns 1
        coEvery { directedAcyclicGraphTagRepository.saveAll(any<Iterable<DirectedAcyclicGraphTagEntity>>()) } returns flowOf(
            mockk()
        )

        // when
        scenarioDetailsUpdaterImpl.saveOrUpdateScenarios(
            "my-tenant",
            listOf(scenarioSummary),
            factoryEntity
        )

        //then
        coVerifyOrder {
            scenarioRepository.findByFactoryId("my-tenant", 543)
            directedAcyclicGraphRepository.deleteByScenarioIdIn(listOf(54125))
            directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(listOf(235))
            scenarioRepository.updateAll(listOf(existingScenarioEntity.copy(enabled = true, dags = emptyList())))
            directedAcyclicGraphRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphEntity(
                        id = -1,
                        version = now,
                        scenarioId = 54125,
                        name = "test-dag",
                        root = true,
                        singleton = false,
                        underLoad = true,
                        numberOfSteps = 5,
                        tags = emptyList()
                    )
                )
            )
            directedAcyclicGraphTagRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphTagEntity(
                        id = -1,
                        directedAcyclicGraphId = 38671,
                        key = "test_dag_tag1",
                        value = dag.tags["test_dag_tag1"]!!
                    )
                )
            )
        }
        confirmVerified(
            scenarioRepository,
            directedAcyclicGraphRepository,
            directedAcyclicGraphTagRepository
        )
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
    ) = ScenarioEntity(
        id = 32,
        version = now,
        factoryId = factoryId,
        name = name,
        scenarioVersion = "0.1",
        builtAt = now.minusSeconds(3),
        defaultMinionsCount = 1,
        enabled = enabled,
        dags = listOf(
            DirectedAcyclicGraphEntity(
                id = 235,
                version = now,
                scenarioId = 32,
                name = dag.name,
                root = dag.isRoot,
                singleton = dag.isSingleton,
                underLoad = dag.isUnderLoad,
                numberOfSteps = dag.numberOfSteps,
                tags = dag.tags.map { (key, value) -> DirectedAcyclicGraphTagEntity(7522, key, value) })
        )
    )

}