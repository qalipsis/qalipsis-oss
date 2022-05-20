package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import io.micronaut.data.exceptions.DataAccessException
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryTagEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

internal class FactoryRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var factoryRepository: FactoryRepository

    @Inject
    private lateinit var factoryStateRepository: FactoryStateRepository

    @Inject
    private lateinit var factoryTagRepository: FactoryTagRepository

    @Inject
    private lateinit var scenarioRepository: ScenarioRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campaignFactoryRepository: CampaignFactoryRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    private val factoryPrototype = FactoryEntity(
        nodeId = "the-node-id",
        registrationTimestamp = Instant.now(),
        registrationNodeId = "the-registration-node-id",
        unicastChannel = "unicast-channel"
    )

    private val tenantPrototype =
        TenantEntity(Instant.now(), "my-tenant", "test-tenant")

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        factoryRepository.deleteAll()
        campaignRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    internal fun `should not save factories twice with the same node ID`() = testDispatcherProvider.run {
        val savedTenant = tenantRepository.save(tenantPrototype.copy())
        factoryRepository.save(factoryPrototype.copy(tenantId = savedTenant.id))
        assertThrows<DataAccessException> {
            factoryRepository.save(factoryPrototype.copy(tenantId = savedTenant.id))
        }
    }

    @Test
    fun `should update the version when the factory is updated`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val saved = factoryRepository.save(factoryPrototype.copy(tenantId = tenant.id))

        // when
        val updated = factoryRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    internal fun `should find the factory by node id with tenant reference`() = testDispatcherProvider.run {
        // given
        val savedTenant = tenantRepository.save(tenantPrototype.copy())
        val factory = factoryRepository.save(factoryPrototype.copy(tenantId = savedTenant.id))
        val tags = mutableListOf<FactoryTagEntity>()
        factoryTagRepository.saveAll(
            listOf(
                FactoryTagEntity(factory.id, "key-1", "value-1"),
                FactoryTagEntity(factory.id, "key-2", "value-2")
            )
        ).collect { tags.add(it) }

        // when + then
        assertThat(factoryRepository.findByNodeIdIn("my-tenant", listOf("the-node-id")).first()).isDataClassEqualTo(
            factory.copy(tags = tags)
        )
        assertThat(factoryRepository.findByNodeIdIn("my-tenant", listOf("the-other-node-id"))).isEmpty()

        assertThat(factoryRepository.findIdByNodeIdIn("my-tenant", listOf("the-node-id"))).containsOnly(factory.id)
        assertThat(
            factoryRepository.findIdByNodeIdIn(
                "my-tenant", listOf("the-node-id", "the-other-node-id")
            )
        ).containsOnly(factory.id)
        assertThat(factoryRepository.findIdByNodeIdIn("my-tenant", listOf("the-other-node-id"))).isEmpty()
    }

    @Test
    fun `should find the factory by node id with tenant reference and different tenants aren't mixed up`() =
        testDispatcherProvider.run {
            // given
            val savedTenant = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "qalipsis-2"))
            val factory = factoryRepository.save(factoryPrototype.copy(tenantId = savedTenant.id))
            val factory2 = factoryRepository.save(factoryPrototype.copy(tenantId = savedTenant2.id))

            // when + then
            assertThat(factoryRepository.findByNodeIdIn("my-tenant", listOf("the-node-id"))).hasSize(1)
            assertThat(factoryRepository.findByNodeIdIn("qalipsis-2", listOf("the-node-id"))).hasSize(1)
            assertThat(factoryRepository.findByNodeIdIn("my-tenant", listOf("the-other-node-id"))).hasSize(0)

            assertThat(factoryRepository.findIdByNodeIdIn("my-tenant", listOf("the-node-id"))).containsOnly(factory.id)
            assertThat(
                factoryRepository.findIdByNodeIdIn(
                    "qalipsis-2",
                    listOf("the-node-id")
                )
            ).containsOnly(factory2.id)
            assertThat(factoryRepository.findIdByNodeIdIn("my-tenant", listOf("the-other-node-id"))).isEmpty()
            assertThat(factoryRepository.findByNodeIdIn("my-tenant", listOf("the-node-id")).first()).isDataClassEqualTo(
                factory.copy()
            )
            assertThat(
                factoryRepository.findByNodeIdIn("qalipsis-2", listOf("the-node-id")).first()
            ).isDataClassEqualTo(
                factory2.copy()
            )
        }

    @Test
    fun `should find the healthy unused factories that supports the enabled scenarios with factory tags`() =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "qalipsis-two"))
            val factory1 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-1",
                        registrationNodeId = "the-registration-node-id-1",
                        tenantId = savedTenant1.id
                    )
                )
            val tags = mutableListOf<FactoryTagEntity>()
            factoryTagRepository.saveAll(
                listOf(
                    FactoryTagEntity(factory1.id, "key-1", "value-1"),
                    FactoryTagEntity(factory1.id, "key-2", "value-2")
                )
            ).collect { tags.add(it) }
            val factory2 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-2",
                        registrationNodeId = "the-registration-node-id-2",
                        tenantId = savedTenant2.id
                    )
                )
            factoryStateRepository.saveAll(
                listOf(
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(110),
                        latency = 654,
                        state = FactoryStateValue.HEALTHY
                    ),
                    FactoryStateEntity(
                        factoryId = factory2.id,
                        healthTimestamp = Instant.now(),
                        latency = 123,
                        state = FactoryStateValue.HEALTHY
                    )
                )
            ).count()
            scenarioRepository.saveAll(
                listOf(
                    ScenarioEntity(factory1.id, "scenario-1", 500),
                    ScenarioEntity(factory1.id, "scenario-2", 100),
                    ScenarioEntity(factory2.id, "scenario-1", 500)
                )
            ).count()

            // when
            val factoriesForScenarios =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "my-tenant",
                    listOf("scenario-1", "scenario-2")
                )

            // then
            assertThat(factoriesForScenarios).all {
                hasSize(1)
                any { it.transform { factory -> factory.id == factory1.id && factory.tags.isNotEmpty() } }
                transform { it.map(FactoryEntity::id) }.containsOnly(factory1.id)
            }
        }

    @Test
    internal fun `should find the healthy unused factories that supports the enabled scenarios with factory tags with tenant reference`() =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "new-qalipsis"))
            val factory1 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-1",
                        registrationNodeId = "the-registration-node-id-1",
                        tenantId = savedTenant1.id
                    )
                )
            val tags = mutableListOf<FactoryTagEntity>()
            factoryTagRepository.saveAll(
                listOf(
                    FactoryTagEntity(factory1.id, "key-1", "value-1"),
                    FactoryTagEntity(factory1.id, "key-2", "value-2")
                )
            ).collect { tags.add(it) }
            val factory2 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-2",
                        registrationNodeId = "the-registration-node-id-2",
                        tenantId = savedTenant2.id
                    )
                )
            factoryStateRepository.saveAll(
                listOf(
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(110),
                        latency = 654,
                        state = FactoryStateValue.HEALTHY
                    ),
                    FactoryStateEntity(
                        factoryId = factory2.id,
                        healthTimestamp = Instant.now(),
                        latency = 123,
                        state = FactoryStateValue.HEALTHY
                    )
                )
            ).count()
            scenarioRepository.saveAll(
                listOf(
                    ScenarioEntity(factory1.id, "scenario-1", 500),
                    ScenarioEntity(factory1.id, "scenario-2", 100),
                    ScenarioEntity(factory2.id, "scenario-1", 500)
                )
            ).count()

            // when
            val factoriesForScenarios1 =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "my-tenant",
                    listOf("scenario-1", "scenario-2")
                )

            val factoriesForScenarios2 =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "new-qalipsis",
                    listOf("scenario-1", "scenario-2")
                )
            // then
            assertThat(factoriesForScenarios1).all {
                hasSize(1)
                any { it.transform { factory -> factory.id == factory1.id && factory.tags.isNotEmpty() } }
                transform { it.map(FactoryEntity::id) }.containsOnly(factory1.id)
            }

            assertThat(factoriesForScenarios2).all {
                hasSize(1)
                any { it.transform { factory -> factory.id == factory2.id && factory.tags.isNotEmpty() } }
                transform { it.map(FactoryEntity::id) }.containsOnly(factory2.id)
            }
        }

    @Test
    internal fun `should not find the healthy factory that supports the enabled scenarios when attached to a running scenario`() =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "new-qalipsis"))
            val factory1 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-1",
                        registrationNodeId = "the-registration-node-id-1",
                        tenantId = savedTenant1.id
                    )
                )
            val factory2 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-2",
                        registrationNodeId = "the-registration-node-id-2",
                        tenantId = savedTenant2.id
                    )
                )
            factoryStateRepository.saveAll(
                listOf(
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(110),
                        latency = 654,
                        state = FactoryStateValue.HEALTHY
                    ),
                    FactoryStateEntity(
                        factoryId = factory2.id,
                        healthTimestamp = Instant.now(),
                        latency = 123,
                        state = FactoryStateValue.HEALTHY
                    )
                )
            ).count()
            scenarioRepository.saveAll(
                listOf(
                    ScenarioEntity(factory1.id, "scenario-1", 500),
                    ScenarioEntity(factory1.id, "scenario-2", 100),
                    ScenarioEntity(factory2.id, "scenario-1", 500)
                )
            ).count()

            val campaign =
                campaignRepository.save(
                    CampaignEntity(
                        campaignName = "the-campaign-1", end = null, tenantId = savedTenant2.id, configurer = 1
                    )
                )
            campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory2.id, discarded = false))

            // when
            val factoriesForScenarios =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "my-tenant",
                    listOf("scenario-1", "scenario-2")
                )

            // then
            assertThat(factoriesForScenarios).all {
                hasSize(1)
                transform { it.map(FactoryEntity::id) }.containsOnly(factory1.id)
            }
        }

    @Test
    internal fun `should find the healthy factory that supports the enabled scenarios when attached to a completed scenario`() =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "new-qalipsis"))
            val factory1 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-1",
                        registrationNodeId = "the-registration-node-id-1",
                        tenantId = savedTenant1.id
                    )
                )
            val factory2 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-2",
                        registrationNodeId = "the-registration-node-id-2",
                        tenantId = savedTenant2.id
                    )
                )
            factoryStateRepository.saveAll(
                listOf(
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(110),
                        latency = 654,
                        state = FactoryStateValue.HEALTHY
                    ),
                    FactoryStateEntity(
                        factoryId = factory2.id,
                        healthTimestamp = Instant.now(),
                        latency = 123,
                        state = FactoryStateValue.HEALTHY
                    )
                )
            ).count()
            scenarioRepository.saveAll(
                listOf(
                    ScenarioEntity(factory1.id, "scenario-1", 500),
                    ScenarioEntity(factory1.id, "scenario-2", 100),
                    ScenarioEntity(factory2.id, "scenario-1", 500)
                )
            ).count()

            val campaign = campaignRepository.save(
                CampaignEntity(
                    campaignName = "the-campaign-1",
                    end = Instant.now(),
                    tenantId = savedTenant2.id,
                    configurer = 1
                )
            )
            campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory2.id, discarded = false))

            // when
            val factoriesForScenarios1 =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "my-tenant",
                    listOf("scenario-1", "scenario-2")
                )

            val factoriesForScenarios2 =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "new-qalipsis",
                    listOf("scenario-1", "scenario-2")
                )

            // then
            assertThat(factoriesForScenarios1).all {
                hasSize(1)
                transform { it.map(FactoryEntity::id) }.containsOnly(factory1.id)
            }
            assertThat(factoriesForScenarios2).all {
                hasSize(1)
                transform { it.map(FactoryEntity::id) }.containsOnly(factory2.id)
            }
        }

    @Test
    internal fun `should find the healthy factory that supports the enabled scenarios when discarded in a running scenario`() =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "new-qalipsis"))
            val factory1 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-1",
                        registrationNodeId = "the-registration-node-id-1",
                        tenantId = savedTenant1.id
                    )
                )

            val factory2 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-2",
                        registrationNodeId = "the-registration-node-id-2",
                        tenantId = savedTenant2.id
                    )
                )
            factoryStateRepository.saveAll(
                listOf(
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(110),
                        latency = 654,
                        state = FactoryStateValue.HEALTHY
                    ),
                    FactoryStateEntity(
                        factoryId = factory2.id,
                        healthTimestamp = Instant.now(),
                        latency = 123,
                        state = FactoryStateValue.HEALTHY
                    )
                )
            ).count()
            scenarioRepository.saveAll(
                listOf(
                    ScenarioEntity(factory1.id, "scenario-1", 500),
                    ScenarioEntity(factory1.id, "scenario-2", 100),
                    ScenarioEntity(factory2.id, "scenario-1", 500)
                )
            ).count()

            val campaign =
                campaignRepository.save(
                    CampaignEntity(
                        campaignName = "the-campaign-1",
                        end = null,
                        tenantId = savedTenant2.id,
                        configurer = 1
                    )
                )
            campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory2.id, discarded = true))

            // when
            val factoriesForScenarios1 =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "my-tenant",
                    listOf("scenario-1", "scenario-2")
                )
            val factoriesForScenarios2 =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "new-qalipsis",
                    listOf("scenario-1", "scenario-2")
                )

            // then
            assertThat(factoriesForScenarios1).all {
                hasSize(1)
                transform { it.map(FactoryEntity::id) }.containsOnly(factory1.id)
            }
            assertThat(factoriesForScenarios2).all {
                hasSize(1)
                transform { it.map(FactoryEntity::id) }.containsOnly(factory2.id)
            }
        }

    @Test
    internal fun `should not find the healthy factories that supports the disabled scenarios`() =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "new-qalipsis"))
            val factory1 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-1",
                        registrationNodeId = "the-registration-node-id-1",
                        tenantId = savedTenant1.id
                    )
                )
            val factory2 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-2",
                        registrationNodeId = "the-registration-node-id-2",
                        tenantId = savedTenant2.id
                    )
                )
            factoryStateRepository.saveAll(
                listOf(
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(110),
                        latency = 654,
                        state = FactoryStateValue.HEALTHY
                    ),
                    FactoryStateEntity(
                        factoryId = factory2.id,
                        healthTimestamp = Instant.now(),
                        latency = 123,
                        state = FactoryStateValue.HEALTHY
                    )
                )
            ).count()
            scenarioRepository.saveAll(
                listOf(
                    ScenarioEntity(factory1.id, "scenario-1", 500),
                    ScenarioEntity(factory1.id, "scenario-2", 100),
                    ScenarioEntity(factory2.id, "scenario-1", 500, enabled = false)
                )
            ).count()

            // when
            val factoriesForScenarios =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "my-tenant",
                    listOf("scenario-1", "scenario-2")
                )

            // then
            assertThat(factoriesForScenarios).all {
                hasSize(1)
                transform { it.map(FactoryEntity::id) }.containsOnly(factory1.id)
            }
        }

    @Test
    internal fun `should not find the unhealthy factories that supports the enabled scenarios`() =
        testDispatcherProvider.run {
            // given
            val savedTenant1 = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "new-qalipsis"))
            val factory1 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-1",
                        registrationNodeId = "the-registration-node-id-1",
                        tenantId = savedTenant1.id
                    )
                )
            val factory2 =
                factoryRepository.save(
                    factoryPrototype.copy(
                        nodeId = "the-node-id-2",
                        registrationNodeId = "the-registration-node-id-2",
                        tenantId = savedTenant2.id
                    )
                )
            factoryStateRepository.saveAll(
                listOf(
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(110),
                        latency = 654,
                        state = FactoryStateValue.HEALTHY
                    ),
                    FactoryStateEntity(
                        factoryId = factory1.id,
                        healthTimestamp = Instant.now() - Duration.ofSeconds(80),
                        latency = 654,
                        state = FactoryStateValue.UNHEALTHY
                    ),
                    FactoryStateEntity(
                        factoryId = factory2.id,
                        healthTimestamp = Instant.now(),
                        latency = 123,
                        state = FactoryStateValue.HEALTHY
                    )
                )
            ).count()
            scenarioRepository.saveAll(
                listOf(
                    ScenarioEntity(factory1.id, "scenario-1", 500),
                    ScenarioEntity(factory1.id, "scenario-2", 100),
                    ScenarioEntity(factory2.id, "scenario-1", 500)
                )
            ).count()

            // when
            val factoriesForScenarios =
                factoryRepository.getAvailableFactoriesForScenarios(
                    "new-qalipsis",
                    listOf("scenario-1", "scenario-2")
                )

            // then
            assertThat(factoriesForScenarios).all {
                hasSize(1)
                transform { it.map(FactoryEntity::id) }.containsOnly(factory2.id)
            }
        }
}