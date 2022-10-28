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

package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.DataAccessException
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

/**
 * @author rklymenko
 */
internal class ScenarioRepositoryIntegrationTest : PostgresqlTemplateTest() {

    private lateinit var scenario: ScenarioEntity

    @Inject
    private lateinit var repository: ScenarioRepository

    @Inject
    private lateinit var dagRepository: DirectedAcyclicGraphRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    private val tenantPrototype =
        TenantEntity(
            Instant.now(),
            "my-tenant",
            "test-tenant",
        )

    @BeforeEach
    internal fun setup(factoryRepository: FactoryRepository) = testDispatcherProvider.run {
        val savedTenant = tenantRepository.save(tenantPrototype.copy(reference = "hello"))
        val factory = factoryRepository.save(
            FactoryEntity(
                nodeId = "the-node",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "test",
                unicastChannel = "unicast-channel",
                tenantId = savedTenant.id
            )
        )
        scenario = ScenarioEntity(factory.id, "test", 1)
    }

    @AfterEach
    internal fun tearDown(factoryRepository: FactoryRepository) = testDispatcherProvider.run {
        factoryRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    fun `should save a single scenario`() = testDispatcherProvider.run {
        // when
        val saved = repository.save(scenario.copy())

        // then
        assertThat(repository.findAll().toList()).hasSize(1)
        assertThat(repository.findById(saved.id)).isNotNull().isDataClassEqualTo(saved)
    }

    @Test
    internal fun `should not save scenario on not-existing factory`() = testDispatcherProvider.run {
        assertThrows<DataAccessException> {
            repository.save(scenario.copy(factoryId = -1))
        }
    }

    @Test
    fun `should update the scenario`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(scenario.copy())

        // when
        repository.update(saved.copy(defaultMinionsCount = 6254128))

        // then
        assertThat(repository.findById(saved.id)).isNotNull().all {
            prop(ScenarioEntity::defaultMinionsCount).isEqualTo(6254128)
            prop(ScenarioEntity::version).isNotNull().isGreaterThan(saved.version)
        }
    }

    @Test
    fun `should disable the scenario by ID`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(scenario.copy())

        // when
        repository.deleteById(saved.id)

        // then
        assertThat(repository.findById(saved.id)!!.enabled).isFalse()
    }

    @Test
    fun `should disable the scenario by entity`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(scenario.copy())

        // when
        repository.delete(saved)

        // then
        assertThat(repository.findById(saved.id)!!.enabled).isFalse()
    }


    @Test
    fun `should disable the scenario by entities`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(scenario.copy())

        // when
        repository.deleteAll(listOf(saved))

        // then
        assertThat(repository.findById(saved.id)!!.enabled).isFalse()
    }

    @Test
    internal fun `should not save two scenarios with the same name on the same factory`() = testDispatcherProvider.run {
        repository.save(scenario.copy())
        assertThrows<DataAccessException> {
            repository.save(scenario.copy())
        }
    }

    @Test
    internal fun `should save two scenarios with the same name on different factories`(factoryRepository: FactoryRepository) =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant.id
                )
            )

            // when
            repository.save(scenario.copy())
            repository.save(scenario.copy(factoryId = factory.id))

            // then
            assertThat(repository.findAll().toList()).hasSize(2)
        }

    @Test
    internal fun `should list the enabled scenarios for the provided names`(factoryRepository: FactoryRepository) =
        testDispatcherProvider.run {
            // given
            val tenant1 = tenantRepository.save(tenantPrototype.copy())
            val tenant2 = tenantRepository.save(tenantPrototype.copy(reference = "my-other-tenant"))
            val factory1 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant1.id
                )
            )
            val factory2 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "any",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant2.id
                )
            )
            repository.save(scenario.copy())
            val scenario2 = repository.save(scenario.copy(name = "another-name", factoryId = factory2.id))
            dagRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphEntity(
                        scenarioId = scenario2.id,
                        name = "dag-A",
                        isRoot = true,
                        singleton = false,
                        underLoad = true,
                        numberOfSteps = 13
                    )
                )
            ).toList()
            val scenario3 = repository.save(scenario.copy(factoryId = factory1.id))
            dagRepository.saveAll(
                listOf(
                    DirectedAcyclicGraphEntity(
                        scenarioId = scenario3.id,
                        name = "dag-1",
                        isRoot = true,
                        singleton = false,
                        underLoad = true,
                        numberOfSteps = 13
                    ),
                    DirectedAcyclicGraphEntity(
                        scenarioId = scenario3.id,
                        name = "dag-2",
                        isRoot = true,
                        singleton = false,
                        underLoad = true,
                        numberOfSteps = 13
                    )
                )
            ).toList()
            repository.save(scenario.copy(factoryId = factory2.id, enabled = false))

            // when + then
            assertThat(
                repository.findActiveByName("my-tenant", listOf("test", "another-name"))
            ).all {
                hasSize(1)
                index(0).all {
                    prop(ScenarioEntity::id).isEqualTo(scenario3.id)
                    prop(ScenarioEntity::dags).hasSize(2)
                }
            }
            assertThat(
                repository.findActiveByName("my-other-tenant", listOf("test", "another-name"))
            ).all {
                hasSize(1)
                index(0).all {
                    prop(ScenarioEntity::id).isEqualTo(scenario2.id)
                    prop(ScenarioEntity::dags).hasSize(1)
                }
            }
        }

    @Test
    internal fun `should list the enabled scenarios for the provided names with tenant reference`(factoryRepository: FactoryRepository) =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "new-qalipsis"))
            val factory1 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = savedTenant1.id
                )
            )
            val factory2 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "any",
                    unicastChannel = "unicast-channel",
                    tenantId = savedTenant2.id
                )
            )
            repository.save(scenario.copy())
            val scenario2 = repository.save(scenario.copy(factoryId = factory2.id, name = "another-name"))
            val scenario3 = repository.save(scenario.copy(factoryId = factory1.id))
            repository.save(scenario.copy(factoryId = factory2.id, enabled = false))

            // when + then
            assertThat(repository.findActiveByName("my-tenant", listOf("test")).map { it.id }).containsOnly(
                scenario3.id
            )
            assertThat(repository.findActiveByName("new-qalipsis", listOf("another-name")).map { it.id }).containsOnly(
                scenario2.id
            )
        }

    @Test
    internal fun `should list the scenarios of the provided factory with tenant reference`(factoryRepository: FactoryRepository) =
        testDispatcherProvider.run {
            // given
            val savedTenant = tenantRepository.save(tenantPrototype.copy())
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = savedTenant.id
                )
            )
            val scenario1 = repository.save(scenario.copy())
            repository.save(scenario.copy(name = "another-name"))
            val scenario3 = repository.save(scenario.copy(factoryId = factory.id))

            // when + then
            assertThat(repository.findByFactoryId("my-tenant", scenario1.factoryId)).isEmpty()
            assertThat(
                repository.findByFactoryId("my-tenant", factory.id).map { it.id }).containsOnly(scenario3.id)
        }

    @Test
    fun `should list the scenarios of the provided factory with tenant reference and different tenants aren't mixed up`(
        factoryRepository: FactoryRepository
    ) =
        testDispatcherProvider.run {
            // given
            val savedTenant = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "qalipsis-2"))
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = savedTenant.id
                )
            )
            val factory2 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = savedTenant2.id
                )
            )

            val scenario1 = repository.save(scenario.copy(factoryId = factory.id))
            val scenario2 = repository.save(scenario.copy(factoryId = factory2.id))

            // when + then
            assertThat(repository.findByFactoryId("my-tenant", scenario2.factoryId)).isEmpty()
            assertThat(repository.findByFactoryId("qalipsis-2", scenario1.factoryId)).isEmpty()
            assertThat(
                repository.findByFactoryId("my-tenant", factory.id).map { it.id }).containsOnly(scenario1.id)
            assertThat(
                repository.findByFactoryId("qalipsis-2", factory2.id).map { it.id }).containsOnly(scenario2.id)
        }


    @Test
    internal fun `should delete the scenarios attached to a deleted factory`(factoryRepository: FactoryRepository) =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant.id
                )
            )
            repository.save(scenario.copy(factoryId = factory.id))
            val scenario = repository.save(scenario.copy(name = "another-name"))
            repository.save(scenario.copy(factoryId = factory.id))

            // when
            factoryRepository.deleteById(factory.id)

            // then
            assertThat(repository.findAll().map { it.id }.toList()).containsOnly(scenario.id)
        }

    @Test
    internal fun `should list the enabled scenarios with tenant reference`(
        factoryRepository: FactoryRepository,
        factoryStateRepository: FactoryStateRepository
    ) =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy(reference = "new"))
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "new-qalipsis"))
            val factory1 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = savedTenant1.id
                )
            )
            val factory2 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "any",
                    unicastChannel = "unicast-channel",
                    tenantId = savedTenant2.id
                )
            )
            factoryStateRepository.saveAll(
                listOf(
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(110),
                        latency = 654,
                        state = FactoryStateValue.IDLE
                    ),
                    FactoryStateEntity(
                        factoryId = factory2.id,
                        healthTimestamp = Instant.now(),
                        latency = 123,
                        state = FactoryStateValue.IDLE
                    )
                )
            ).count()

            repository.save(scenario.copy(factoryId = factory1.id, defaultMinionsCount = 3, name = "one"))
            val scenario2 = repository.save(scenario.copy(factoryId = factory2.id, name = "four"))
            repository.save(scenario.copy(factoryId = factory1.id, name = "three"))
            repository.save(scenario.copy(factoryId = factory1.id, defaultMinionsCount = 5, name = "two"))
            val scenario5 = repository.save(scenario.copy(factoryId = factory2.id, name = "another-name"))

            // when + then
            repository.findAllActiveWithSorting("new", "default_minions_count").map { it.id }
            val result2 = repository.findAllActiveWithSorting("new-qalipsis", "name").map { it.id }
            assertThat(result2).containsOnly(scenario2.id, scenario5.id)
            assertThat(result2.get(0)).isEqualTo(scenario5.id)
            assertThat(result2.get(1)).isEqualTo(scenario2.id)
        }
}