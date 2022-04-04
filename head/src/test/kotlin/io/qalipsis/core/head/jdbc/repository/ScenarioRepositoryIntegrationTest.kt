package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.DataAccessException
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * @author rklymenko
 */
internal class ScenarioRepositoryIntegrationTest : PostgresqlTemplateTest() {

    private lateinit var scenario: ScenarioEntity

    @Inject
    private lateinit var repository: ScenarioRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    private val tenantPrototype =
        TenantEntity(
            Instant.now(),
            "qalipsis",
            "test-tenant",
        )

    @BeforeEach
    internal fun setup(factoryRepository: FactoryRepository) = testDispatcherProvider.run {
        val factory = factoryRepository.save(
            FactoryEntity(
                nodeId = "the-node",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "test",
                unicastChannel = "unicast-channel"
            )
        )
        scenario = ScenarioEntity(factory.id, "test", 1)
    }

    @AfterEach
    internal fun tearDown(factoryRepository: FactoryRepository) = testDispatcherProvider.run {
        factoryRepository.deleteAll()
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
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel"
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
            val factory1 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel"
                )
            )
            val factory2 = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "any",
                    unicastChannel = "unicast-channel"
                )
            )
            val scenario1 = repository.save(scenario.copy())
            val scenario2 = repository.save(scenario.copy(name = "another-name"))
            val scenario3 = repository.save(scenario.copy(factoryId = factory1.id))
            repository.save(scenario.copy(factoryId = factory2.id, enabled = false))

            // when + then
            assertThat(repository.findActiveByName(listOf("test")).map { it.id }).containsOnly(
                scenario1.id,
                scenario3.id
            )
            assertThat(repository.findActiveByName(listOf("another-name")).map { it.id }).containsOnly(scenario2.id)
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
            val scenario1 = repository.save(scenario.copy())
            val scenario2 = repository.save(scenario.copy(factoryId = factory2.id, name = "another-name"))
            val scenario3 = repository.save(scenario.copy(factoryId = factory1.id))
            repository.save(scenario.copy(factoryId = factory2.id, enabled = false))

            // when + then
            assertThat(repository.findActiveByName(listOf("test"), "qalipsis").map { it.id }).containsOnly(
                scenario3.id
            )
            assertThat(repository.findActiveByName(listOf("another-name"), "new-qalipsis").map { it.id }).containsOnly(
                scenario2.id
            )
        }

    @Test
    internal fun `should list the scenarios of the provided factory`(factoryRepository: FactoryRepository) =
        testDispatcherProvider.run {
            // given
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel"
                )
            )
            val scenario1 = repository.save(scenario.copy())
            val scenario2 = repository.save(scenario.copy(name = "another-name"))
            val scenario3 = repository.save(scenario.copy(factoryId = factory.id))

            // when + then
            assertThat(repository.findByFactoryId(scenario1.factoryId).map { it.id })
                .containsOnly(scenario1.id, scenario2.id)
            assertThat(repository.findByFactoryId(factory.id).map { it.id }).containsOnly(scenario3.id)
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
            val scenario2 = repository.save(scenario.copy(name = "another-name"))
            val scenario3 = repository.save(scenario.copy(factoryId = factory.id))

            // when + then
            assertThat(repository.findByFactoryId(scenario1.factoryId, "qalipsis")).isEmpty()
            assertThat(repository.findByFactoryId(factory.id, "qalipsis").map { it.id }).containsOnly(scenario3.id)
        }

    @Test
    internal fun `should delete the scenarios attached to a deleted factory`(factoryRepository: FactoryRepository) =
        testDispatcherProvider.run {
            // given
            val factory = factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-other-node" + Math.random(),
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "test",
                    unicastChannel = "unicast-channel"
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
}