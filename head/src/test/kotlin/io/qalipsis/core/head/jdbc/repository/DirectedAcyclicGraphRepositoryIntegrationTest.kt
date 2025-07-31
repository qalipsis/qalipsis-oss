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

package io.qalipsis.core.head.persistence.dagRepository

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.qalipsis.core.head.jdbc.entity.TenantEntityForTest
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphEntity
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphTagEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphRepository
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphTagRepository
import io.qalipsis.core.head.jdbc.repository.FactoryRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepositoryForTest
import io.qalipsis.core.postgres.AbstractPostgreSQLTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

/**
 * @author rklymenko
 */
internal class DirectedAcyclicGraphRepositoryIntegrationTest : AbstractPostgreSQLTest() {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var dag: DirectedAcyclicGraphEntity

    private lateinit var scenario: ScenarioEntity

    @Inject
    private lateinit var repository: DirectedAcyclicGraphRepository

    @Inject
    private lateinit var selectorRepository: DirectedAcyclicGraphTagRepository

    @BeforeAll
    internal fun setUpAll(
        factoryRepository: FactoryRepository,
        scenarioRepository: ScenarioRepository,
        tenantRepository: TenantRepositoryForTest
    ) =
        testDispatcherProvider.run {
            val tenant = tenantRepository.save(TenantEntityForTest(reference = "my-tenant"))
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-node",
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant.id
                )
            )
            scenario = scenarioRepository.save(
                ScenarioEntity(
                    factoryId = factory.id,
                    scenarioName = "test",
                    scenarioDescription = "my-test",
                    scenarioVersion = "0.1",
                    builtAt = Instant.now(),
                    defaultMinionsCount = 123
                )
            )
            dag = DirectedAcyclicGraphEntity(
                scenarioId = scenario.id,
                name = "dag-1",
                root = false,
                singleton = false,
                underLoad = true,
                numberOfSteps = 21,
                tags = listOf(
                    DirectedAcyclicGraphTagEntity(-1, "key-1", "value-1"),
                    DirectedAcyclicGraphTagEntity(-1, "key-2", "value-2")
                )
            )
        }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        selectorRepository.deleteAll()
        repository.deleteAll()
    }

    @Test
    fun `should save a dag with tags and fetch by ID`() = testDispatcherProvider.run {
        // when
        val saved = repository.save(dag.copy())
        selectorRepository.saveAll(dag.tags.map { it.copy(directedAcyclicGraphId = saved.id) }).count()

        // then
        assertThat(repository.findAll().toList()).hasSize(1)
        assertThat(selectorRepository.findAll().toList()).hasSize(2)

        // when
        val resultingEntity = repository.findById(saved.id)

        // then
        assertThat(resultingEntity).isNotNull().all {
            prop(DirectedAcyclicGraphEntity::id).isGreaterThan(0)
            prop(DirectedAcyclicGraphEntity::version).isNotNull().isGreaterThan(Instant.EPOCH)
            prop(DirectedAcyclicGraphEntity::scenarioId).isEqualTo(scenario.id)
            prop(DirectedAcyclicGraphEntity::name).isEqualTo("dag-1")
            prop(DirectedAcyclicGraphEntity::root).isFalse()
            prop(DirectedAcyclicGraphEntity::singleton).isFalse()
            prop(DirectedAcyclicGraphEntity::underLoad).isTrue()
            prop(DirectedAcyclicGraphEntity::numberOfSteps).isEqualTo(21)
            prop(DirectedAcyclicGraphEntity::tags).all {
                hasSize(2)
                any {
                    it.all {
                        prop(DirectedAcyclicGraphTagEntity::key).isEqualTo("key-1")
                        prop(DirectedAcyclicGraphTagEntity::value).isEqualTo("value-1")
                    }
                }
                any {
                    it.all {
                        prop(DirectedAcyclicGraphTagEntity::key).isEqualTo("key-2")
                        prop(DirectedAcyclicGraphTagEntity::value).isEqualTo("value-2")
                    }
                }
            }
        }
    }

    @Test
    internal fun `should not save selector on a missing dag`() = testDispatcherProvider.run {
        assertThrows<R2dbcDataIntegrityViolationException> {
            selectorRepository.save(DirectedAcyclicGraphTagEntity(-1, "key-1", "value-1"))
        }
    }

    @Test
    internal fun `should not save dags twice with the same name`() = testDispatcherProvider.run {
        repository.save(dag.copy())
        assertThrows<R2dbcDataIntegrityViolationException> {
            repository.save(dag.copy())
        }
    }

    @Test
    internal fun `should not save tags twice with same key for same dag`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(dag.copy())

        // when
        selectorRepository.save(DirectedAcyclicGraphTagEntity(saved.id, "key-1", "value-1"))
        assertThrows<R2dbcDataIntegrityViolationException> {
            selectorRepository.save(DirectedAcyclicGraphTagEntity(saved.id, "key-1", "value-1"))
        }
    }

    @Test
    internal fun `should save tags twice with same key for different dags`() = testDispatcherProvider.run {
        // given
        val saved1 = repository.save(dag.copy())
        val saved2 = repository.save(dag.copy(name = "another name"))

        // when
        selectorRepository.save(DirectedAcyclicGraphTagEntity(saved1.id, "key-1", "value-1"))
        selectorRepository.save(DirectedAcyclicGraphTagEntity(saved2.id, "key-1", "value-1"))

        // then
        assertThat(selectorRepository.findAll().toList()).hasSize(2)
    }

    @Test
    fun `should update the version when the dag is updated`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(dag.copy())

        // when
        val updated = repository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    fun `should update the entity tags`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(dag.copy())
        val tags =
            selectorRepository.saveAll(dag.tags.map { it.copy(directedAcyclicGraphId = saved.id) }).toList()

        // when
        // Tests the strategy of update for the tags attached to a dag, as used in the PersistentFactoryService.
        selectorRepository.deleteAll(tags.subList(0, 1))
        selectorRepository.updateAll(listOf(tags[1].withValue("other-than-value-2"))).count()
        selectorRepository.saveAll(listOf(DirectedAcyclicGraphTagEntity(saved.id, "key-3", "value-3"))).count()

        // then
        assertThat(repository.findById(saved.id)).isNotNull().all {
            prop(DirectedAcyclicGraphEntity::id).isGreaterThan(0)
            prop(DirectedAcyclicGraphEntity::version).isEqualTo(saved.version)
            prop(DirectedAcyclicGraphEntity::scenarioId).isEqualTo(scenario.id)
            prop(DirectedAcyclicGraphEntity::name).isEqualTo("dag-1")
            prop(DirectedAcyclicGraphEntity::root).isFalse()
            prop(DirectedAcyclicGraphEntity::singleton).isFalse()
            prop(DirectedAcyclicGraphEntity::underLoad).isTrue()
            prop(DirectedAcyclicGraphEntity::numberOfSteps).isEqualTo(21)
            prop(DirectedAcyclicGraphEntity::tags).all {
                hasSize(2)
                any {
                    it.all {
                        prop(DirectedAcyclicGraphTagEntity::key).isEqualTo("key-2")
                        prop(DirectedAcyclicGraphTagEntity::value).isEqualTo("other-than-value-2")
                    }
                }
                any {
                    it.all {
                        prop(DirectedAcyclicGraphTagEntity::key).isEqualTo("key-3")
                        prop(DirectedAcyclicGraphTagEntity::value).isEqualTo("value-3")
                    }
                }
            }
        }
    }

    @Test
    fun `should delete the dag and its tags`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(dag.copy())
        selectorRepository.saveAll(dag.tags.map { it.copy(directedAcyclicGraphId = saved.id) }).count()
        assertThat(selectorRepository.findAll().toList()).isNotEmpty()

        // when
        repository.deleteById(saved.id)

        // then
        assertThat(repository.findAll().toList()).isEmpty()
        assertThat(selectorRepository.findAll().toList()).isEmpty()
    }

    @Test
    internal fun `should find by scenarios IDs`(scenarioRepository: ScenarioRepository) = testDispatcherProvider.run {
        // given
        val otherScenario = scenarioRepository.save(scenario.copy(name = "other-scenario"))
        val anotherScenario = scenarioRepository.save(scenario.copy(name = "another-scenario"))

        repository.save(dag.copy(name = "dag-1")).also { dag ->
            selectorRepository.saveAll(dag.tags.map {
                it.copy(
                    directedAcyclicGraphId = dag.id,
                    value = it.value + "-dag-1"
                )
            }).count()
        }
        repository.save(dag.copy(name = "dag-2")).also { dag ->
            selectorRepository.saveAll(dag.tags.map {
                it.copy(
                    directedAcyclicGraphId = dag.id,
                    value = it.value + "-dag-2"
                )
            }).count()
        }
        repository.save(dag.copy(name = "dag-3", scenarioId = otherScenario.id)).also { dag ->
            selectorRepository.saveAll(dag.tags.map {
                it.copy(
                    directedAcyclicGraphId = dag.id,
                    value = it.value + "-dag-3"
                )
            }).count()
        }
        repository.save(dag.copy(name = "dag-4", scenarioId = anotherScenario.id)).also { dag ->
            selectorRepository.saveAll(dag.tags.map {
                it.copy(
                    directedAcyclicGraphId = dag.id,
                    value = it.value + "-dag-4"
                )
            }).count()
        }

        // when
        val dags = repository.findByScenarioIdIn(listOf(scenario.id, anotherScenario.id))

        // then
        assertThat(dags.sortedBy { it.name }).all {
            hasSize(3)
            index(0).all {
                prop(DirectedAcyclicGraphEntity::name).isEqualTo("dag-1")
                prop(DirectedAcyclicGraphEntity::tags).all {
                    hasSize(2)
                    each { it.transform { it.value }.endsWith("dag-1") }
                }
            }
            index(1).all {
                prop(DirectedAcyclicGraphEntity::name).isEqualTo("dag-2")
                prop(DirectedAcyclicGraphEntity::tags).all {
                    hasSize(2)
                    each { it.transform { it.value }.endsWith("dag-2") }
                }
            }
            index(2).all {
                prop(DirectedAcyclicGraphEntity::name).isEqualTo("dag-4")
                prop(DirectedAcyclicGraphEntity::tags).all {
                    hasSize(2)
                    each { it.transform { it.value }.endsWith("dag-4") }
                }
            }
        }
    }


    @Test
    internal fun `should delete by scenarios IDs`(scenarioRepository: ScenarioRepository) = testDispatcherProvider.run {
        // given
        val otherScenario = scenarioRepository.save(scenario.copy(name = "still-another-scenario"))
        val anotherScenario = scenarioRepository.save(scenario.copy(name = "yet-another-scenario"))

        repository.save(dag.copy(name = "dag-1")).also { dag ->
            selectorRepository.saveAll(dag.tags.map {
                it.copy(
                    directedAcyclicGraphId = dag.id,
                    value = it.value + "-dag-1"
                )
            }).count()
        }
        repository.save(dag.copy(name = "dag-2")).also { dag ->
            selectorRepository.saveAll(dag.tags.map {
                it.copy(
                    directedAcyclicGraphId = dag.id,
                    value = it.value + "-dag-2"
                )
            }).count()
        }
        repository.save(dag.copy(name = "dag-3", scenarioId = otherScenario.id)).also { dag ->
            selectorRepository.saveAll(dag.tags.map {
                it.copy(
                    directedAcyclicGraphId = dag.id,
                    value = it.value + "-dag-3"
                )
            }).count()
        }
        repository.save(dag.copy(name = "dag-4", scenarioId = anotherScenario.id)).also { dag ->
            selectorRepository.saveAll(dag.tags.map {
                it.copy(
                    directedAcyclicGraphId = dag.id,
                    value = it.value + "-dag-4"
                )
            }).count()
        }

        // when
        repository.deleteByScenarioIdIn(listOf(scenario.id, anotherScenario.id))

        // then
        assertThat(repository.findAll().toList().sortedBy { it.name }).all {
            hasSize(1)
            index(0).all {
                prop(DirectedAcyclicGraphEntity::name).isEqualTo("dag-3")
                prop(DirectedAcyclicGraphEntity::tags).all {
                    hasSize(2)
                    each { it.transform { it.value }.endsWith("dag-3") }
                }
            }
        }
    }
}