package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.entity.UserEntity
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

internal class CampaignRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var factoryRepository: FactoryRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campagnScenarioRepository: CampaignScenarioRepository

    @Inject
    private lateinit var campaignFactoryRepository: CampaignFactoryRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var userRepository: UserRepository

    private val campaignPrototype =
        CampaignEntity(
            key = "the-campaign-id",
            name = "This is my new campaign",
            speedFactor = 123.0,
            start = Instant.now() - Duration.ofSeconds(173),
            end = Instant.now(),
            result = ExecutionStatus.SUCCESSFUL,
            configurer = 1L // Default user.
        )

    private val tenantPrototype = TenantEntity(Instant.now(), "my-tenant", "test-tenant")

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        factoryRepository.deleteAll()
        tenantRepository.deleteAll()
        kotlin.runCatching {
            val allButDefaultUsers = userRepository.findAll().filterNot { it.username == Defaults.USER }.toList()
            if (allButDefaultUsers.isNotEmpty()) {
                userRepository.deleteAll(allButDefaultUsers)
            }
        }
    }

    @Test
    internal fun `should save then get`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))

        // when
        val fetched = campaignRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().isDataClassEqualTo(saved)
    }

    @Test
    internal fun `should find the ID of the running campaign`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))

        // when + then
        assertThrows<EmptyResultException> {
            campaignRepository.findIdByKeyAndEndIsNull("my-tenant", saved.key)
        }

        // when
        campaignRepository.update(saved.copy(end = null))

        assertThat(campaignRepository.findIdByKeyAndEndIsNull("my-tenant", saved.key)).isEqualTo(saved.id)
    }

    @Test
    fun `should find the ID of the running campaign and different tenants aren't mixed up`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val tenant2 = tenantRepository.save(tenantPrototype.copy(reference = "qalipsis-2"))
            val saved =
                campaignRepository.save(campaignPrototype.copy(key = "1", end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(
                    campaignPrototype.copy(
                        key = "2",
                        name = "new",
                        end = null,
                        tenantId = tenant2.id
                    )
                )

            // when + then
            assertThat(campaignRepository.findIdByKeyAndEndIsNull("my-tenant", saved.key)).isEqualTo(saved.id)

            assertThat(
                campaignRepository.findIdByKeyAndEndIsNull("qalipsis-2", saved2.key)
            ).isEqualTo(saved2.id)

            assertThrows<EmptyResultException> {
                assertThat(campaignRepository.findIdByKeyAndEndIsNull("my-tenant", saved2.key))
            }

            assertThrows<EmptyResultException> {
                assertThat(campaignRepository.findIdByKeyAndEndIsNull("qalipsis-2", saved.key))
            }
        }

    @Test
    fun `should update the version and aborter when the campaign is updated`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))

        // when
        val updated = campaignRepository.update(saved.copy(aborter = 1))

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
        assertThat(saved.aborter).isNull()
        assertThat(updated.aborter).isEqualTo(1)
    }

    @Test
    internal fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))
        val factory =
            factoryRepository.save(
                FactoryEntity(
                    nodeId = "the-node-id",
                    registrationTimestamp = Instant.now(),
                    registrationNodeId = "the-registration-node-id",
                    unicastChannel = "unicast-channel",
                    tenantId = tenant.id
                )
            )
        campagnScenarioRepository.save(CampaignScenarioEntity(saved.id, "the-scenario", 231))
        campaignFactoryRepository.save(CampaignFactoryEntity(saved.id, factory.id, discarded = false))
        assertThat(campagnScenarioRepository.findAll().count()).isEqualTo(1)
        assertThat(campagnScenarioRepository.findAll().count()).isEqualTo(1)

        // when
        campaignRepository.deleteById(saved.id)

        // then
        assertThat(campagnScenarioRepository.findAll().count()).isEqualTo(0)
        assertThat(campagnScenarioRepository.findAll().count()).isEqualTo(0)
    }

    @Test
    internal fun `should close the open campaign`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val alreadyClosedCampaign =
            campaignRepository.save(campaignPrototype.copy(key = "1", end = Instant.now(), tenantId = tenant.id))
        val openCampaign = campaignRepository.save(campaignPrototype.copy(key = "2", end = null, tenantId = tenant.id))
        val otherOpenCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "3",
                    name = "other-campaign",
                    end = null,
                    tenantId = tenant.id
                )
            )

        // when
        val beforeCall = Instant.now()
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignRepository.close("my-tenant", "2", ExecutionStatus.FAILED)

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(openCampaign.start)
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNotNull().isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::result).isEqualTo(ExecutionStatus.FAILED)
        }
    }

    @Test
    fun `should find all campaigns in tenant without filter but with paging`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            val saved = campaignRepository.save(campaignPrototype.copy(key = "1", end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(campaignPrototype.copy(key = "2", end = null, tenantId = tenant.id))

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    Pageable.from(0, 1, Sort.of(Sort.Order("key")))
                ).content
            )
                .containsOnly(saved)
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    Pageable.from(1, 1, Sort.of(Sort.Order("key")))
                ).content
            )
                .containsOnly(saved2)
        }

    @Test
    fun `should find all campaigns in tenant with filter and paging`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(campaignPrototype.copy(key = "anyone-1", end = null, tenantId = tenant.id))
            val saved3 =
                campaignRepository.save(campaignPrototype.copy(key = "anyone-2", end = null, tenantId = tenant.id))

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%NyO%", "%NoNe%"),
                    Pageable.from(0, 2, Sort.of(Sort.Order("key")))
                )
            ).containsOnly(saved2, saved3)
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%NyO%", "%NoNe%"),
                    Pageable.from(0, 2, Sort.of(Sort.Order("key", Sort.Order.Direction.DESC, true)))
                )
            ).containsOnly(saved3, saved2)
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%NyO%", "%NoNe%"),
                    Pageable.from(0, 1, Sort.of(Sort.Order("key")))
                )
            ).containsOnly(saved2)
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%NyO%", "%NoNe%"),
                    Pageable.from(1, 1, Sort.of(Sort.Order("key")))
                )
            ).containsOnly(saved3)

            assertThat(
                campaignRepository.findAll("other-tenant", listOf("%NyO%", "%NoNe%"), Pageable.from(0, 1))
            ).isEmpty()
        }

    @Test
    fun `should find all campaigns in tenant with filter on key`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(campaignPrototype.copy(key = "anyone", end = null, tenantId = tenant.id))

            // when + then
            assertThat(
                campaignRepository.findAll("my-tenant-2", listOf("%NyO%", "%NoNe%"), Pageable.from(0, 1))
            ).containsOnly(saved2)
            assertThat(
                campaignRepository.findAll("other-tenant", listOf("%NyO%", "%NoNe%"), Pageable.from(0, 1))
            ).isEmpty()
        }

    @Test
    fun `should find all campaigns in tenant with filter on name`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(
                    campaignPrototype.copy(
                        key = "the-key",
                        name = "The other name",
                        end = null,
                        tenantId = tenant.id
                    )
                )

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%OtH%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).containsOnly(saved2)
            assertThat(
                campaignRepository.findAll(
                    "other-tenant",
                    listOf("%OtH%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).isEmpty()
        }

    @Test
    fun `should find all campaigns in tenant with filter on configurer username`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            val user = userRepository.save(UserEntity(username = "John Doe"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(
                    campaignPrototype.copy(
                        key = "the-key",
                        name = "The other name",
                        end = null,
                        tenantId = tenant.id,
                        configurer = user.id
                    )
                )

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%HN%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).containsOnly(saved2)
            assertThat(
                campaignRepository.findAll(
                    "other-tenant",
                    listOf("%HN%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).isEmpty()
        }

    @Test
    fun `should find all campaigns in tenant with filter on configurer display name`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            val user = userRepository.save(UserEntity(username = "foo", displayName = "John Doe"))
            campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
            val saved2 =
                campaignRepository.save(
                    campaignPrototype.copy(
                        key = "the-key",
                        name = "The other name",
                        end = null,
                        tenantId = tenant.id,
                        configurer = user.id
                    )
                )

            // when + then
            assertThat(
                campaignRepository.findAll(
                    "my-tenant-2",
                    listOf("%HN%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).containsOnly(saved2)
            assertThat(
                campaignRepository.findAll(
                    "other-tenant",
                    listOf("%HN%", "%NoNe%"),
                    Pageable.from(0, 1)
                )
            ).isEmpty()
        }

    @Test
    internal fun `should find campaign by key`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-11", "test-tenant"))
        val saved = campaignRepository.save(campaignPrototype.copy(key = "name-11", tenantId = tenant.id))

        // when
        val fetched = campaignRepository.findByKey("tenant-11", saved.key)

        // then
        assertThat(fetched).isNotNull().isDataClassEqualTo(saved)
    }
}