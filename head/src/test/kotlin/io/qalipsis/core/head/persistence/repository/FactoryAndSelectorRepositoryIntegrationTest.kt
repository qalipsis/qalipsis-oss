package io.qalipsis.core.head.persistence.repository

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
import io.micronaut.data.exceptions.EmptyResultException
import io.qalipsis.core.head.persistence.entity.FactoryEntity
import io.qalipsis.core.head.persistence.entity.FactorySelectorEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * @author rklymenko
 */
internal class FactoryAndSelectorRepositoryIntegrationTest : PostgresqlTemplateTest() {

    private val factory = FactoryEntity(
        nodeId = "the-node", registrationTimestamp = Instant.now(), registrationNodeId = "test", selectors =
        listOf(
            FactorySelectorEntity(-1, "key-1", "value-1"),
            FactorySelectorEntity(-1, "key-2", "value-2")
        )
    )

    @Inject
    private lateinit var repository: FactoryRepository

    @Inject
    private lateinit var selectorRepository: FactorySelectorRepository

    @AfterEach
    internal fun tearDown(): Unit = runBlocking {
        selectorRepository.deleteAll()
        repository.deleteAll()
    }

    @Test
    fun `should save a factory with selectors and fetch by node ID`() = testDispatcherProvider.run {
        // when
        val saved = repository.save(factory.copy())
        selectorRepository.saveAll(factory.selectors.map { it.copy(factoryId = saved.id) }).count()

        // then
        assertThat(repository.findAll().toList()).hasSize(1)
        assertThat(selectorRepository.findAll().toList()).hasSize(2)

        // when
        val resultingEntity = repository.findByNodeId("the-node").first()

        // then
        assertThat(resultingEntity).all {
            prop(FactoryEntity::id).isGreaterThan(0)
            prop(FactoryEntity::version).isNotNull().isGreaterThan(Instant.EPOCH)
            prop(FactoryEntity::nodeId).isEqualTo("the-node")
            prop(FactoryEntity::registrationNodeId).isEqualTo("test")
            prop(FactoryEntity::registrationTimestamp).isEqualTo(factory.registrationTimestamp)
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
    }

    @Test
    internal fun `should not save selector on a missing factory`() = testDispatcherProvider.run {
        assertThrows<DataAccessException> {
            selectorRepository.save(FactorySelectorEntity(-1, "key-1", "value-1"))
        }
    }

    @Test
    internal fun `should not save factories twice with the same node ID`() = testDispatcherProvider.run {
        repository.save(factory.copy())
        assertThrows<DataAccessException> {
            repository.save(factory.copy())
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
    fun `should update the version when the factory is updated`() = testDispatcherProvider.run {
        // given
        val saved = repository.save(factory.copy())

        // when
        val updated = repository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
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
        assertThat(repository.findByNodeId("the-node").first()).all {
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
        assertThat(repository.findByNodeId("the-node")).isEmpty()
        assertThat(selectorRepository.findAll().toList()).isEmpty()
    }

    @Test
    fun `should find not factory id by node id and throw EmptyResultException`() = testDispatcherProvider.run {
        assertThrows<EmptyResultException> {
            repository.findIdByNodeId(factory.nodeId)
        }
    }

    @Test
    fun `should find factory id by node id`() = testDispatcherProvider.run {
        //given
        val saved = repository.save(factory.copy())

        //when
        val factoryId = repository.findIdByNodeId(factory.nodeId)

        //then
        assertThat(factoryId).isEqualTo(saved.id)
    }
}