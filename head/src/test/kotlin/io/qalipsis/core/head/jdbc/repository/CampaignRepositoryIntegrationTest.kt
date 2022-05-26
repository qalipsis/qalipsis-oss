package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
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

    private val campaignPrototype =
        CampaignEntity(
            campaignName = "the-campaign-id",
            speedFactor = 123.0,
            start = Instant.now() - Duration.ofSeconds(173),
            end = Instant.now(),
            result = ExecutionStatus.SUCCESSFUL,
            configurer = 1
        )

    private val tenantPrototype =
        TenantEntity(
            Instant.now(),
            "my-tenant",
            "test-tenant",
        )

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        factoryRepository.deleteAll()
        tenantRepository.deleteAll()
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
        val savedTenant = tenantRepository.save(tenantPrototype.copy())
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = savedTenant.id))

        // when + then
        assertThrows<EmptyResultException> {
            campaignRepository.findIdByNameAndEndIsNull("my-tenant", saved.name)
        }

        // when
        campaignRepository.update(saved.copy(end = null))

        assertThat(campaignRepository.findIdByNameAndEndIsNull("my-tenant", saved.name)).isEqualTo(saved.id)
    }

    @Test
    fun `should find the ID of the running campaign and different tenants aren't mixed up`() =
        testDispatcherProvider.run {
            // given
            val savedTenant = tenantRepository.save(tenantPrototype.copy())
            val savedTenant2 = tenantRepository.save(tenantPrototype.copy(reference = "qalipsis-2"))
            val saved = campaignRepository.save(campaignPrototype.copy(end = null, tenantId = savedTenant.id))
            val saved2 =
                campaignRepository.save(campaignPrototype.copy(name = "new", end = null, tenantId = savedTenant2.id))

            // when + then
            assertThat(campaignRepository.findIdByNameAndEndIsNull("my-tenant", saved.name)).isEqualTo(saved.id)
            assertThat(
                campaignRepository.findIdByNameAndEndIsNull("qalipsis-2", saved2.name)
            ).isEqualTo(saved2.id)
            assertThrows<EmptyResultException> {
                assertThat(campaignRepository.findIdByNameAndEndIsNull("my-tenant", saved2.name))
            }
            assertThrows<EmptyResultException> {
                assertThat(campaignRepository.findIdByNameAndEndIsNull("qalipsis-2", saved.name))
            }
        }

    @Test
    fun `should update the version when the campaign is updated`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))

        // when
        val updated = campaignRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    internal fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
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
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val alreadyClosedCampaign =
            campaignRepository.save(campaignPrototype.copy(end = Instant.now(), tenantId = tenant.id))
        val openCampaign = campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
        val otherOpenCampaign =
            campaignRepository.save(campaignPrototype.copy(name = "other-campaign", end = null, tenantId = tenant.id))

        // when
        val beforeCall = Instant.now()
        delay(50) // Adds a delay because it happens that the time in the DB container is slightly in the past.
        campaignRepository.close(campaignPrototype.name, ExecutionStatus.FAILED)

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
    fun `should find all campaigns in tenant`() =
        testDispatcherProvider.run {
            // given
            val savedTenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            val saved = campaignRepository.save(campaignPrototype.copy(end = null, tenantId = savedTenant.id))
            val saved2 =
                campaignRepository.save(campaignPrototype.copy(name = "new", end = null, tenantId = savedTenant.id))
            val campaigns = listOf(saved, saved2)

            // when + then
            assertThat(campaignRepository.findAll("my-tenant-2", Pageable.from(0, 20)).content).isEqualTo(campaigns)
        }

    @Test
    fun `should find all campaigns in tenant with filter`() =
        testDispatcherProvider.run {
            // given
            val savedTenant = tenantRepository.save(tenantPrototype.copy(reference = "my-tenant-2"))
            val saved = campaignRepository.save(campaignPrototype.copy(end = null, tenantId = savedTenant.id))
            val saved2 =
                campaignRepository.save(campaignPrototype.copy(name = "new-one", end = null, tenantId = savedTenant.id))
            val campaigns = listOf(saved2)

            // when + then
            assertThat(campaignRepository.findAll("my-tenant-2", "%new%")).isEqualTo(campaigns)
        }
}