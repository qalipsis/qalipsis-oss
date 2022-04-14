package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.matchesPredicate
import io.micronaut.data.exceptions.DataAccessException
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
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
internal class FactoryStateRepositoryIntegrationTest : PostgresqlTemplateTest() {

    private lateinit var state: FactoryStateEntity

    @Inject
    private lateinit var repository: FactoryStateRepository

    @BeforeEach
    internal fun setup(factoryRepository: FactoryRepository, tenantRepository: TenantRepository) =
        testDispatcherProvider.run {
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "qalipsis", "test-tenant"))
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
                    FactoryStateValue.HEALTHY
                )
        }

    @AfterEach
    internal fun tearDown(factoryRepository: FactoryRepository) = testDispatcherProvider.run {
        factoryRepository.deleteAll()
    }

    @Test
    fun `should save a single state`() = testDispatcherProvider.run {
        // when
        val saved = repository.save(state.copy())

        // then
        assertThat(repository.findAll().toList()).hasSize(1)
        assertThat(repository.findById(saved.id)).isNotNull().isDataClassEqualTo(saved)
    }

    @Test
    internal fun `should not save state on non-existing factory`() = testDispatcherProvider.run {
        assertThrows<DataAccessException> {
            repository.save(state.copy(factoryId = -1))
        }
    }

    @Test
    fun `should delete for a factory state before a timestamp only`(
        factoryRepository: FactoryRepository,
        tenantRepository: TenantRepository
    ) =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "qalipsis", "test-tenant"))
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
            repository.saveAll(
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
                        FactoryStateValue.HEALTHY
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
                        FactoryStateValue.UNREGISTERED
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
                        FactoryStateValue.HEALTHY
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
                        FactoryStateValue.UNREGISTERED
                    )
                )
            ).count()

            // when
            val deleted = repository.deleteByFactoryIdAndHealthTimestampBefore(factory2.id, cutoff)

            // then
            assertThat(deleted).isEqualTo(2)
            assertThat(repository.findAll().toList()).all {
                hasSize(6)
                each { it.matchesPredicate { it.factoryId == factory1.id || it.healthTimestamp >= cutoff } }
            }
        }

    @Test
    internal fun `should delete the states attached to a deleted factory`(
        factoryRepository: FactoryRepository,
        tenantRepository: TenantRepository
    ) =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "qalipsis", "test-tenant"))
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant.id
                )
            )
            repository.save(state.copy(factoryId = factory.id))
            val state = repository.save(state.copy())
            repository.save(state.copy(factoryId = factory.id))

            // when
            factoryRepository.deleteById(factory.id)

            // then
            assertThat(repository.findAll().map { it.id }.toList()).containsOnly(state.id)
        }
}