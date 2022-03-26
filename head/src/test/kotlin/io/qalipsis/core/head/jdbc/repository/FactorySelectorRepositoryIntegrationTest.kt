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
import io.qalipsis.core.head.jdbc.entity.FactorySelectorEntity
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
internal class FactorySelectorRepositoryIntegrationTest : PostgresqlTemplateTest() {

    private val factory = FactoryEntity(
        nodeId = "the-node", registrationTimestamp = Instant.now(), registrationNodeId = "test",
        unicastChannel = "unicast-channel",
        selectors = listOf(
            FactorySelectorEntity(-1, "key-1", "value-1"),
            FactorySelectorEntity(-1, "key-2", "value-2")
        )
    )

    @Inject
    private lateinit var repository: FactoryRepository

    @Inject
    private lateinit var selectorRepository: FactorySelectorRepository

    @AfterEach
    internal fun tearDown(): Unit = testDispatcherProvider.run {
        selectorRepository.deleteAll()
        repository.deleteAll()
    }

    @Test
    fun `should save a factory with selectors and fetch by node ID`() = testDispatcherProvider.run {
        // when
        val factory = repository.save(factory.copy())
        selectorRepository.saveAll(this@FactorySelectorRepositoryIntegrationTest.factory.selectors.map {
            it.copy(
                factoryId = factory.id
            )
        }).count()

        // then
        assertThat(repository.findAll().toList()).hasSize(1)
        assertThat(selectorRepository.findAll().toList()).hasSize(2)

        // when
        val resultingEntity = repository.findByNodeIdIn(listOf("the-node")).first()

        // then
        assertThat(resultingEntity).all {
            prop(FactoryEntity::id).isGreaterThan(0)
            prop(FactoryEntity::version).isNotNull().isGreaterThan(Instant.EPOCH)
            prop(FactoryEntity::nodeId).isEqualTo("the-node")
            prop(FactoryEntity::registrationNodeId).isEqualTo("test")
            prop(FactoryEntity::registrationTimestamp).isEqualTo(this@FactorySelectorRepositoryIntegrationTest.factory.registrationTimestamp)
            prop(FactoryEntity::selectors).all {
                hasSize(2)
                any {
                    it.all {
                        prop(FactorySelectorEntity::key).isEqualTo("key-1")
                        prop(FactorySelectorEntity::value).isEqualTo("value-1")
                    }
                }
                any {
                    it.all {
                        prop(FactorySelectorEntity::key).isEqualTo("key-2")
                        prop(FactorySelectorEntity::value).isEqualTo("value-2")
                    }
                }
            }
        }

        // when
        val selectorsOfFactories = selectorRepository.findByFactoryIdIn(listOf(factory.id, -1, Long.MAX_VALUE))

        // then
        assertThat(selectorsOfFactories).isEqualTo(resultingEntity.selectors)
    }

    @Test
    internal fun `should not save selector on a missing factory`() = testDispatcherProvider.run {
        assertThrows<DataAccessException> {
            selectorRepository.save(FactorySelectorEntity(-1, "key-1", "value-1"))
        }
    }

    @Test
    internal fun `should not save selectors twice with same key for same factory`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(factory.copy())

        // when
        selectorRepository.save(FactorySelectorEntity(saved.id, "key-1", "value-1"))
        assertThrows<DataAccessException> {
            selectorRepository.save(FactorySelectorEntity(saved.id, "key-1", "value-1"))
        }
    }

    @Test
    internal fun `should save selectors twice with same key for different factories`() = testDispatcherProvider.run {
        // given
        val saved1 = repository.save(factory.copy())
        val saved2 = repository.save(factory.copy(nodeId = "another node ID"))

        // when
        selectorRepository.save(FactorySelectorEntity(saved1.id, "key-1", "value-1"))
        selectorRepository.save(FactorySelectorEntity(saved2.id, "key-1", "value-1"))

        // then
        assertThat(selectorRepository.findAll().toList()).hasSize(2)
    }

    @Test
    fun `should update the entity selectors`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(factory.copy())
        val selectors =
            selectorRepository.saveAll(factory.selectors.map { it.copy(factoryId = saved.id) }).toList()

        // when
        // Tests the strategy of update for the selectors attached to a factory, as used in the PersistentFactoryService.
        selectorRepository.deleteAll(selectors.subList(0, 1))
        selectorRepository.updateAll(listOf(selectors[1].withValue("other-than-value-2"))).count()
        selectorRepository.saveAll(listOf(FactorySelectorEntity(saved.id, "key-3", "value-3"))).count()

        // then
        assertThat(repository.findByNodeIdIn(listOf("the-node")).first()).all {
            prop(FactoryEntity::id).isGreaterThan(0)
            prop(FactoryEntity::version).isEqualTo(saved.version)
            prop(FactoryEntity::nodeId).isEqualTo("the-node")
            prop(FactoryEntity::registrationNodeId).isEqualTo("test")
            prop(FactoryEntity::registrationTimestamp).isEqualTo(factory.registrationTimestamp)
            prop(FactoryEntity::selectors).all {
                hasSize(2)
                any {
                    it.all {
                        prop(FactorySelectorEntity::key).isEqualTo("key-2")
                        prop(FactorySelectorEntity::value).isEqualTo("other-than-value-2")
                    }
                }
                any {
                    it.all {
                        prop(FactorySelectorEntity::key).isEqualTo("key-3")
                        prop(FactorySelectorEntity::value).isEqualTo("value-3")
                    }
                }
            }
        }
    }

    @Test
    fun `should delete the factory and its selectors`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(factory.copy())
        selectorRepository.saveAll(factory.selectors.map { it.copy(factoryId = saved.id) }).count()
        assertThat(selectorRepository.findAll().toList()).isNotEmpty()

        // when
        repository.deleteById(saved.id)

        // then
        assertThat(repository.findByNodeIdIn(listOf("the-node"))).isEmpty()
        assertThat(selectorRepository.findAll().toList()).isEmpty()
    }

}