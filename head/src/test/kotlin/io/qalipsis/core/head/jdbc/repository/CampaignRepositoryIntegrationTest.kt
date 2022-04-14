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
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
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
            result = ExecutionStatus.SUCCESSFUL
        )

    private val tenantPrototype =
        TenantEntity(
            Instant.now(),
            "qalipsis",
            "test-tenant",
        )

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        factoryRepository.deleteAll()
    }

    @Test
    internal fun `should save then get`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "qalipsis", "test-tenant"))
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
            campaignRepository.findIdByNameAndEndIsNull("qalipsis", saved.name)
        }

        // when
        campaignRepository.update(saved.copy(end = null))

        assertThat(campaignRepository.findIdByNameAndEndIsNull("qalipsis", saved.name)).isEqualTo(saved.id)
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
            assertThat(campaignRepository.findIdByNameAndEndIsNull("qalipsis", saved.name)).isEqualTo(saved.id)
            assertThat(
                campaignRepository.findIdByNameAndEndIsNull("qalipsis-2", saved2.name)
            ).isEqualTo(saved2.id)
            assertThrows<EmptyResultException> {
                assertThat(campaignRepository.findIdByNameAndEndIsNull("qalipsis", saved2.name))
            }
            assertThrows<EmptyResultException> {
                assertThat(campaignRepository.findIdByNameAndEndIsNull("qalipsis-2", saved.name))
            }
        }

    @Test
    fun `should update the version when the campaign is updated`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "qalipsis", "test-tenant"))
        val saved = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))

        // when
        val updated = campaignRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    internal fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "qalipsis", "test-tenant"))
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
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "qalipsis", "test-tenant"))
        val alreadyClosedCampaign =
            campaignRepository.save(campaignPrototype.copy(end = Instant.now(), tenantId = tenant.id))
        val openCampaign = campaignRepository.save(campaignPrototype.copy(end = null, tenantId = tenant.id))
        val otherOpenCampaign =
            campaignRepository.save(campaignPrototype.copy(name = "other-campaign", end = null, tenantId = tenant.id))

        // when
        val beforeCall = Instant.now()
        campaignRepository.close(campaignPrototype.name, ExecutionStatus.FAILED)

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThan(openCampaign.version)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(openCampaign.start)
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNotNull().isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::result).isEqualTo(ExecutionStatus.FAILED)
        }
    }
}