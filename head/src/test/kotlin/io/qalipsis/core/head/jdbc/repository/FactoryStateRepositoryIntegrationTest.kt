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
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.matchesPredicate
import assertk.assertions.size
import com.qalipsis.core.head.jdbc.entity.TenantEntityForTest
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.postgres.AbstractPostgreSQLTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

/**
 * @author rklymenko
 */
internal class FactoryStateRepositoryIntegrationTest : AbstractPostgreSQLTest() {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var state: FactoryStateEntity

    @Inject
    private lateinit var factoryRepository: FactoryRepository

    @Inject
    private lateinit var tenantRepository: TenantRepositoryForTest

    @Inject
    private lateinit var factoryStateRepository: FactoryStateRepository

    private lateinit var tenant: TenantEntityForTest

    @BeforeEach
    internal fun setup() =
        testDispatcherProvider.run {
            tenant = tenantRepository.save(TenantEntityForTest(reference = "my-tenant"))
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-node",
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant.id
                )
            )
            state =
                FactoryStateEntity(
                    Instant.now(),
                    factory.id,
                    healthTimestamp = Instant.now(),
                    0,
                    FactoryStateValue.IDLE
                )
        }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        factoryRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    fun `should save a single state`() = testDispatcherProvider.run {
        // when
        val saved = factoryStateRepository.save(state.copy())

        // then
        assertThat(factoryStateRepository.findAll().toList()).hasSize(1)
        assertThat(factoryStateRepository.findById(saved.id)).isNotNull().isDataClassEqualTo(saved)
    }

    @Test
    internal fun `should not save state on non-existing factory`() = testDispatcherProvider.run {
        assertThrows<R2dbcDataIntegrityViolationException> {
            factoryStateRepository.save(state.copy(factoryId = -1))
        }
    }

    @Test
    fun `should delete for a factory state before a timestamp only`() = testDispatcherProvider.run {
        // given
        val cutoff = Instant.now() - Duration.ofHours(2)
        val factory1 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-1",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant.id
            )
        )
        val factory2 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-2",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant.id
            )
        )
        factoryStateRepository.saveAll(
            listOf(
                FactoryStateEntity(
                    Instant.now(),
                    factoryId = factory1.id,
                    healthTimestamp = cutoff - Duration.ofHours(4),
                    0,
                    FactoryStateValue.REGISTERED
                ),
                FactoryStateEntity(
                    Instant.now(),
                    factoryId = factory1.id,
                    healthTimestamp = cutoff - Duration.ofHours(2),
                    0,
                    FactoryStateValue.IDLE
                ),
                FactoryStateEntity(
                    Instant.now(),
                    factoryId = factory1.id,
                    healthTimestamp = cutoff.plusMillis(1),
                    0,
                    FactoryStateValue.UNHEALTHY
                ),
                FactoryStateEntity(
                    Instant.now(),
                    factoryId = factory1.id,
                    healthTimestamp = Instant.now(),
                    0,
                    FactoryStateValue.OFFLINE
                ),

                FactoryStateEntity(
                    Instant.now(),
                    factoryId = factory2.id,
                    healthTimestamp = cutoff - Duration.ofHours(4),
                    0,
                    FactoryStateValue.REGISTERED
                ),
                FactoryStateEntity(
                    Instant.now(),
                    factoryId = factory2.id,
                    healthTimestamp = cutoff - Duration.ofHours(2),
                    0,
                    FactoryStateValue.IDLE
                ),
                FactoryStateEntity(
                    Instant.now(),
                    factoryId = factory2.id,
                    healthTimestamp = cutoff.plusMillis(1),
                    0,
                    FactoryStateValue.UNHEALTHY
                ),
                FactoryStateEntity(
                    Instant.now(),
                    factoryId = factory2.id,
                    healthTimestamp = Instant.now(),
                    0,
                    FactoryStateValue.OFFLINE
                )
            )
        ).count()

        // when
        val deleted = factoryStateRepository.deleteByFactoryIdAndHealthTimestampBefore(factory2.id, cutoff)

        // then
        assertThat(deleted).isEqualTo(2)
        assertThat(factoryStateRepository.findAll().toList()).all {
            hasSize(6)
            each { it.matchesPredicate { it.factoryId == factory1.id || it.healthTimestamp >= cutoff } }
        }
    }

    @Test
    internal fun `should delete the states attached to a deleted factory`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(TenantEntityForTest(reference = "my-other-tenant"))
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant.id
                )
            )
            factoryStateRepository.save(state.copy(factoryId = factory.id))
            val state = factoryStateRepository.save(state.copy())
            factoryStateRepository.save(state.copy(factoryId = factory.id))

            // when
            factoryRepository.deleteById(factory.id)

            // then
            assertThat(factoryStateRepository.findAll().map { it.id }.toList()).containsOnly(state.id)
        }

    @Test
    fun `should retrieve latest factory state for each factory id per tenant`() = testDispatcherProvider.run {
        // given
        val tenant2 = tenantRepository.save(TenantEntityForTest(reference = "tenant-2"))
        val cutoff = Instant.now() - Duration.ofSeconds(30)
        val now = Instant.now()
        val factory1 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-1",
                registrationTimestamp = now,
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant.id
            )
        )
        val factory2 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-2",
                registrationTimestamp = now,
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant.id
            )
        )
        val factory3 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-3",
                registrationTimestamp = now,
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant.id
            )
        )

        val factory4 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-4",
                registrationTimestamp = now,
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant.id
            )
        )

        val factory5 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-5",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant2.id
            )
        )

        val factory6 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-6",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant.id
            )
        )

        val factory7 = factoryRepository.save(
            FactoryEntity(
                nodeId = "factory-7",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "random-node",
                unicastChannel = "unicast-channel",
                tenantId = tenant.id
            )
        )

        factoryStateRepository.saveAll(
            listOf(
                FactoryStateEntity(
                    now,
                    factoryId = factory1.id,
                    healthTimestamp = cutoff - Duration.ofHours(4),
                    0,
                    FactoryStateValue.REGISTERED // Will be considered as UNHEALTHY.
                ),
                FactoryStateEntity(
                    now,
                    factoryId = factory2.id,
                    healthTimestamp = cutoff - Duration.ofHours(1),
                    0,
                    FactoryStateValue.OFFLINE // Will be considered as OFFLINE.
                ),
                FactoryStateEntity(
                    now,
                    factoryId = factory3.id,
                    healthTimestamp = cutoff.plusMillis(1),
                    0,
                    FactoryStateValue.IDLE // Will be considered as IDLE.
                ),
                FactoryStateEntity(
                    now,
                    factoryId = factory4.id,
                    healthTimestamp = cutoff.plusMillis(1),
                    0,
                    FactoryStateValue.OFFLINE // Will be considered as OFFLINE.
                ),
                FactoryStateEntity(
                    now,
                    factoryId = factory5.id,
                    healthTimestamp = Instant.now(),
                    0,
                    FactoryStateValue.IDLE // Is not in the tenant.
                ),
                FactoryStateEntity(
                    now,
                    factoryId = factory6.id,
                    healthTimestamp = Instant.now().minusMillis(1),
                    0,
                    FactoryStateValue.REGISTERED // Is ignored because not the most recent.
                ),
                FactoryStateEntity(
                    now,
                    factoryId = factory6.id,
                    healthTimestamp = Instant.now(),
                    0,
                    FactoryStateValue.IDLE // Will be considered as IDLE.
                ),
                FactoryStateEntity(
                    now,
                    factoryId = factory7.id,
                    healthTimestamp = cutoff - Duration.ofMinutes(5),
                    0,
                    FactoryStateValue.IDLE // Will be considered as UNHEALTHY.
                )

            )
        ).toList()

        // when
        val factoryStateEntities = factoryStateRepository.countCurrentFactoryStatesByTenant(tenant.reference)

        // then
        assertThat(factoryStateEntities).all {
            size().isEqualTo(3)
            containsExactlyInAnyOrder(
                FactoryStateRepository.FactoryStateCount(FactoryStateValue.IDLE, 2),
                FactoryStateRepository.FactoryStateCount(FactoryStateValue.OFFLINE, 2),
                FactoryStateRepository.FactoryStateCount(FactoryStateValue.UNHEALTHY, 2)
            )
        }
    }

}