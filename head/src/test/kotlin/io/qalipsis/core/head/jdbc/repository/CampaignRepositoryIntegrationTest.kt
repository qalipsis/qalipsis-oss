package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
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
import java.time.temporal.ChronoUnit

internal class CampaignRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var factoryRepository: FactoryRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

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
            scheduledMinions = 123,
            hardTimeout = true,
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
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "the-scenario", minionsCount = 231))
        campaignFactoryRepository.save(CampaignFactoryEntity(saved.id, factory.id, discarded = false))

        assertThat(campaignScenarioRepository.findAll().count()).isEqualTo(1)
        assertThat(campaignFactoryRepository.findAll().count()).isEqualTo(1)

        // when
        campaignRepository.deleteById(saved.id)

        // then
        assertThat(campaignScenarioRepository.findAll().count()).isEqualTo(0)
        assertThat(campaignFactoryRepository.findAll().count()).isEqualTo(0)
    }

    @Test
    internal fun `should start the created campaign with a timeout`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val alreadyClosedCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "1",
                    start = Instant.now(),
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )
        val openCampaign = campaignRepository.save(
            campaignPrototype.copy(
                key = "2",
                start = null,
                tenantId = tenant.id,
                end = null,
                result = null
            )
        )
        val otherOpenCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "3",
                    name = "other-campaign",
                    start = null,
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )

        // when
        val beforeCall = Instant.now()
        val start = Instant.now().plusSeconds(12)
        campaignRepository.start("my-tenant", "2", start, start.plusSeconds(123))

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(start)
            prop(CampaignEntity::timeout).isEqualTo(start.plusSeconds(123))
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNull()
            prop(CampaignEntity::result).isNull()
        }
    }


    @Test
    internal fun `should start the created campaign without timeout`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val alreadyClosedCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "1",
                    start = Instant.now(),
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )
        val openCampaign = campaignRepository.save(
            campaignPrototype.copy(
                key = "2",
                start = null,
                tenantId = tenant.id,
                end = null,
                result = null
            )
        )
        val otherOpenCampaign =
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "3",
                    name = "other-campaign",
                    start = null,
                    tenantId = tenant.id,
                    end = null,
                    result = null
                )
            )

        // when
        val beforeCall = Instant.now()
        val start = Instant.now().plusSeconds(12)
        campaignRepository.start("my-tenant", "2", start, null)

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(start)
            prop(CampaignEntity::timeout).isNull()
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNull()
            prop(CampaignEntity::result).isNull()
        }
    }

    @Test
    internal fun `should complete the open campaign`() = testDispatcherProvider.run {
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
        campaignRepository.complete("my-tenant", "2", ExecutionStatus.FAILED)

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

    @Test
    internal fun `should return only campaign keys of the tenant`() = testDispatcherProvider.run {
        //given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-1",
                name = "campaign-1",
                end = null,
                tenantId = tenant.id
            )
        )
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-2",
                name = "campaign-2",
                end = null,
                tenantId = tenant2.id
            )
        )
        campaignRepository.save(
            campaignPrototype.copy(
                key = "key-3",
                name = "campaign-3",
                end = null,
                tenantId = tenant.id
            )
        )

        // when
        val campaignKeys =
            campaignRepository.findKeyByTenantAndKeyIn(tenant = tenant.reference, keys = listOf("key-1", "key-3"))
        // then
        assertThat(campaignKeys).all {
            hasSize(2)
            containsOnly("key-1", "key-3")
        }

        //when + then
        assertThat(
            campaignRepository.findKeyByTenantAndKeyIn(
                tenant = tenant.reference,
                keys = listOf("key-4", "key-2")
            )
        ).isEmpty()
    }

    @Test
    internal fun `should return only campaign keys of the tenant and campaign names patterns`() =
        testDispatcherProvider.run {
            //given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
            val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-1",
                    name = "campaign-1",
                    end = null,
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-2",
                    name = "CAMPAIGN-2",
                    end = null,
                    tenantId = tenant2.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-3",
                    name = "camp-3",
                    end = null,
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-4",
                    name = "CAMPAIGN-4",
                    end = null,
                    tenantId = tenant.id
                )
            )

            // when
            val campaignKeys = campaignRepository.findKeysByTenantAndNamePatterns("tenant-ref", listOf("camp%"))
            // then
            assertThat(campaignKeys).all {
                hasSize(3)
                containsOnly("key-1", "key-3", "key-4")
            }

            //when + then
            assertThat(campaignRepository.findKeysByTenantAndNamePatterns("tenant-ref", listOf("camp-_"))).containsOnly(
                "key-3"
            )
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("%IG%")
                )
            ).containsOnly("key-1", "key-4")
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("%IG%", "ca_")
                )
            ).containsOnly("key-1", "key-4")
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("%IG%", "ca%")
                )
            ).containsOnly("key-1", "key-3", "key-4")
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("%4")
                )
            ).containsOnly("key-4")
            assertThat(campaignRepository.findKeysByTenantAndNamePatterns("tenant-ref", listOf("GN%"))).isEmpty()
            assertThat(
                campaignRepository.findKeysByTenantAndNamePatterns(
                    "tenant-ref",
                    listOf("GN%", "%4")
                )
            ).containsOnly("key-4")
        }

    @Test
    internal fun `should return only campaign keys by tenant id and campaign names patterns`() =
        testDispatcherProvider.run {
            //given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
            val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-1",
                    name = "campaign-1",
                    end = null,
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-2",
                    name = "CAMPAIGN-2",
                    end = null,
                    tenantId = tenant2.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-3",
                    name = "camp-3",
                    end = null,
                    tenantId = tenant.id
                )
            )
            campaignRepository.save(
                campaignPrototype.copy(
                    key = "key-4",
                    name = "CAMPAIGN-4",
                    end = null,
                    tenantId = tenant.id
                )
            )

            // when
            val campaignKeys = campaignRepository.findKeysByTenantIdAndNamePatterns(tenant.id, listOf("camp%"))
            // then
            assertThat(campaignKeys).all {
                hasSize(3)
                containsOnly("key-1", "key-3", "key-4")
            }

            //when + then
            assertThat(
                campaignRepository.findKeysByTenantIdAndNamePatterns(
                    tenant.id,
                    listOf("camp-_")
                )
            ).containsOnly("key-3")
            assertThat(
                campaignRepository.findKeysByTenantIdAndNamePatterns(
                    tenant.id,
                    listOf("%IG%")
                )
            ).containsOnly("key-1", "key-4")
            assertThat(
                campaignRepository.findKeysByTenantIdAndNamePatterns(
                    tenant.id,
                    listOf("%IG%", "ca_", "x")
                )
            ).containsOnly("key-1", "key-4")
            assertThat(
                campaignRepository.findKeysByTenantIdAndNamePatterns(
                    tenant.id,
                    listOf("%IG%", "ca%")
                )
            ).containsOnly(
                "key-1",
                "key-3",
                "key-4"
            )
            assertThat(
                campaignRepository.findKeysByTenantIdAndNamePatterns(
                    tenant.id,
                    listOf("%4")
                )
            ).containsOnly("key-4")
            assertThat(campaignRepository.findKeysByTenantIdAndNamePatterns(tenant.id, listOf("GN%"))).isEmpty()
            assertThat(
                campaignRepository.findKeysByTenantIdAndNamePatterns(
                    tenant.id,
                    listOf("GN%", "%4")
                )
            ).containsOnly("key-4")
        }


    @Test
    internal fun `should return the min start, max end and max duration when all the campaigns are ended`() =
        testDispatcherProvider.run {
            // given
            val start1 = Instant.now().truncatedTo(ChronoUnit.SECONDS) - Duration.ofMinutes(23)
            val end1 = start1 + Duration.ofSeconds(16)

            val start2 = Instant.now().truncatedTo(ChronoUnit.SECONDS) - Duration.ofMinutes(22)
            val end2 = start2 + Duration.ofSeconds(1_236)

            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
            val otherTenant = tenantRepository.save(TenantEntity(Instant.now(), "other-tenant", "other-tenant"))
            campaignRepository.saveAll(
                listOf(
                    campaignPrototype.copy(tenantId = tenant.id, key = "camp-1", start = start1, end = end1),
                    campaignPrototype.copy(tenantId = tenant.id, key = "camp-2", start = start2, end = end2),
                    // "Noise entities" to verify the clauses.
                    campaignPrototype.copy(
                        tenantId = tenant.id,
                        key = "camp-3",
                        start = Instant.EPOCH,
                        end = Instant.now() + Duration.ofDays(365)
                    ),
                    campaignPrototype.copy(
                        tenantId = otherTenant.id,
                        key = "camp-1",
                        start = Instant.EPOCH,
                        end = Instant.now() + Duration.ofDays(365)
                    ),
                    campaignPrototype.copy(
                        tenantId = otherTenant.id,
                        key = "camp-2",
                        start = Instant.EPOCH,
                        end = Instant.now() + Duration.ofDays(365)
                    )
                )
            ).count()

            // when
            val result = campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))

            // then
            assertThat(result).isNotNull().all {
                prop(CampaignsInstantsAndDuration::minStart).isEqualTo(start1)
                prop(CampaignsInstantsAndDuration::maxEnd).isEqualTo(end2)
                prop(CampaignsInstantsAndDuration::maxDurationSec).isEqualTo(Duration.between(start2, end2).toSeconds())
            }
        }

    @Test
    internal fun `should return the min start, max end and max duration when the longest campaigns are not ended`() =
        testDispatcherProvider.run {
            // given
            val start1 = Instant.now().truncatedTo(ChronoUnit.SECONDS) - Duration.ofMinutes(23)
            val end1 = start1 + Duration.ofSeconds(16)

            val start2 = Instant.now().truncatedTo(ChronoUnit.SECONDS) - Duration.ofMinutes(22)

            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
            val otherTenant = tenantRepository.save(TenantEntity(Instant.now(), "other-tenant", "other-tenant"))
            campaignRepository.saveAll(
                listOf(
                    campaignPrototype.copy(tenantId = tenant.id, key = "camp-1", start = start1, end = end1),
                    campaignPrototype.copy(tenantId = tenant.id, key = "camp-2", start = start2, end = null),
                    // "Noise entities" to verify the clauses.
                    campaignPrototype.copy(
                        tenantId = tenant.id,
                        key = "camp-3",
                        start = Instant.EPOCH,
                        end = Instant.now() + Duration.ofDays(365)
                    ),
                    campaignPrototype.copy(
                        tenantId = otherTenant.id,
                        key = "camp-1",
                        start = Instant.EPOCH,
                        end = Instant.now() + Duration.ofDays(365)
                    ),
                    campaignPrototype.copy(
                        tenantId = otherTenant.id,
                        key = "camp-2",
                        start = Instant.EPOCH,
                        end = Instant.now() + Duration.ofDays(365)
                    )
                )
            ).count()

            // when
            val justBeforeQuery = Instant.now()
            val result = campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))

            // then
            assertThat(result).isNotNull().all {
                prop(CampaignsInstantsAndDuration::minStart).isEqualTo(start1)
                prop(CampaignsInstantsAndDuration::maxEnd).isNotNull().isGreaterThanOrEqualTo(justBeforeQuery)
                prop(CampaignsInstantsAndDuration::maxDurationSec).isNotNull()
                    .isGreaterThanOrEqualTo(Duration.ofMinutes(22).toSeconds())
            }
        }

    @Test
    internal fun `should return neither start, end nor duration when no specified campaign exists`() =
        testDispatcherProvider.run {
            // when
            val result = campaignRepository.findInstantsAndDuration("my-tenant", setOf("camp-1", "camp-2"))

            // then
            assertThat(result).isNotNull().all {
                prop(CampaignsInstantsAndDuration::minStart).isNull()
                prop(CampaignsInstantsAndDuration::maxEnd).isNull()
                prop(CampaignsInstantsAndDuration::maxDurationSec).isNull()
            }
        }
}