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
import assertk.assertions.any
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.DataAccessException
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryTagEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * @author rklymenko
 */
internal class FactoryTagRepositoryIntegrationTest : PostgresqlTemplateTest() {

    private val factory = FactoryEntity(
        nodeId = "the-node", registrationTimestamp = Instant.now(), registrationNodeId = "test",
        unicastChannel = "unicast-channel",
        tags = listOf(
            FactoryTagEntity(-1, "key-1", "value-1"),
            FactoryTagEntity(-1, "key-2", "value-2")
        )
    )

    private val tenantPrototype =
        TenantEntity(Instant.now(), "my-tenant", "test-tenant")

    @Inject
    private lateinit var repository: FactoryRepository

    @Inject
    private lateinit var tagRepository: FactoryTagRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @AfterEach
    internal fun tearDown(): Unit = testDispatcherProvider.run {
        tagRepository.deleteAll()
        repository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    fun `should save a factory with tags and fetch by node ID`() = testDispatcherProvider.run {
        // when
        val savedTenant = tenantRepository.save(tenantPrototype.copy())
        val factory = repository.save(factory.copy(tenantId = savedTenant.id))
        tagRepository.saveAll(this@FactoryTagRepositoryIntegrationTest.factory.   tags.map {
            it.copy(
                factoryId = factory.id
            )
        }).count()

        // then
        assertThat(repository.findAll().toList()).hasSize(1)
        assertThat(tagRepository.findAll().toList()).hasSize(2)

        // when
        val resultingEntity = repository.findByNodeIdIn("my-tenant", listOf("the-node")).first()

        // then
        assertThat(resultingEntity).all {
            prop(FactoryEntity::id).isGreaterThan(0)
            prop(FactoryEntity::version).isNotNull().isGreaterThan(Instant.EPOCH)
            prop(FactoryEntity::nodeId).isEqualTo("the-node")
            prop(FactoryEntity::registrationNodeId).isEqualTo("test")
            prop(FactoryEntity::registrationTimestamp).isEqualTo(this@FactoryTagRepositoryIntegrationTest.factory.registrationTimestamp)
            prop(FactoryEntity::tags).all {
                hasSize(2)
                any {
                    it.all {
                        prop(FactoryTagEntity::key).isEqualTo("key-1")
                        prop(FactoryTagEntity::value).isEqualTo("value-1")
                    }
                }
                any {
                    it.all {
                        prop(FactoryTagEntity::key).isEqualTo("key-2")
                        prop(FactoryTagEntity::value).isEqualTo("value-2")
                    }
                }
            }
        }

        // when
        val tagsOfFactories = tagRepository.findByFactoryIdIn(listOf(factory.id, -1, Long.MAX_VALUE))

        // then
        assertThat(tagsOfFactories).isEqualTo(resultingEntity.tags)
    }

    @Test
    internal fun `should not save tag on a missing factory`() = testDispatcherProvider.run {
        assertThrows<DataAccessException> {
            tagRepository.save(FactoryTagEntity(-1, "key-1", "value-1"))
        }
    }

    @Test
    internal fun `should not save tags twice with same key for same factory`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val saved = repository.save(factory.copy(tenantId = tenant.id))

        // when
        tagRepository.save(FactoryTagEntity(saved.id, "key-1", "value-1"))
        assertThrows<DataAccessException> {
            tagRepository.save(FactoryTagEntity(saved.id, "key-1", "value-1"))
        }
    }

    @Test
    internal fun `should save tags twice with same key for different factories`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val saved1 = repository.save(factory.copy(tenantId = tenant.id))
        val saved2 = repository.save(factory.copy(nodeId = "another node ID", tenantId = tenant.id))

        // when
        tagRepository.save(FactoryTagEntity(saved1.id, "key-1", "value-1"))
        tagRepository.save(FactoryTagEntity(saved2.id, "key-1", "value-1"))

        // then
        assertThat(tagRepository.findAll().toList()).hasSize(2)
    }

    @Test
    fun `should update the entity tags`() = testDispatcherProvider.run {
        // given
        val savedTenant = tenantRepository.save(tenantPrototype.copy())
        val saved = repository.save(factory.copy(tenantId = savedTenant.id))
        val tags =
            tagRepository.saveAll(factory.tags.map { it.copy(factoryId = saved.id) }).toList()

        // when
        // Tests the strategy of update for the tags attached to a factory, as used in the PersistentFactoryService.
        tagRepository.deleteAll(tags.subList(0, 1))
        tagRepository.updateAll(listOf(tags[1].withValue("other-than-value-2"))).count()
        tagRepository.saveAll(listOf(FactoryTagEntity(saved.id, "key-3", "value-3"))).count()

        // then
        assertThat(repository.findByNodeIdIn("my-tenant", listOf("the-node")).first()).all {
            prop(FactoryEntity::id).isGreaterThan(0)
            prop(FactoryEntity::version).isEqualTo(saved.version)
            prop(FactoryEntity::nodeId).isEqualTo("the-node")
            prop(FactoryEntity::registrationNodeId).isEqualTo("test")
            prop(FactoryEntity::registrationTimestamp).isEqualTo(factory.registrationTimestamp)
            prop(FactoryEntity::tags).all {
                hasSize(2)
                any {
                    it.all {
                        prop(FactoryTagEntity::key).isEqualTo("key-2")
                        prop(FactoryTagEntity::value).isEqualTo("other-than-value-2")
                    }
                }
                any {
                    it.all {
                        prop(FactoryTagEntity::key).isEqualTo("key-3")
                        prop(FactoryTagEntity::value).isEqualTo("value-3")
                    }
                }
            }
        }
    }

    @Test
    fun `should delete the factory and its tags`() = testDispatcherProvider.run {
        // given
        val savedTenant = tenantRepository.save(tenantPrototype.copy())
        val saved = repository.save(factory.copy(tenantId = savedTenant.id))
        tagRepository.saveAll(factory.tags.map { it.copy(factoryId = saved.id) }).count()
        assertThat(tagRepository.findAll().toList()).isNotEmpty()

        // when
        repository.deleteById(saved.id)

        // then
        assertThat(repository.findByNodeIdIn("my-tenant", listOf("the-node"))).isEmpty()
        assertThat(tagRepository.findAll().toList()).isEmpty()
    }

}