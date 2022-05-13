package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class CampaignReportRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var campaignReportRepository: CampaignReportRepository

    @Inject
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    private lateinit var campaignReportPrototype: CampaignReportEntity

    @BeforeEach
    fun init() = testDispatcherProvider.run {
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
        val campaignPrototype =
            CampaignEntity(
                campaignName = "the-campaign-id",
                speedFactor = 123.0,
                start = Instant.now() - Duration.ofSeconds(173),
                end = Instant.now(),
                result = ExecutionStatus.SUCCESSFUL,
                tenantId = tenant.id,
                configurer = "qalipsis-user"
            )
        val campaign = campaignRepository.save(campaignPrototype.copy())
        campaignReportPrototype =
            CampaignReportEntity(
                campaignId = campaign.id,
                startedMinions = 1000,
                completedMinions = 990,
                successfulExecutions = 990,
                failedExecutions = 10
            )
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        campaignReportRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = campaignReportRepository.save(campaignReportPrototype.copy())

        // when
        val fetched = campaignReportRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(CampaignReportEntity::id).isEqualTo(saved.id)
            prop(CampaignReportEntity::campaignId).isEqualTo(saved.campaignId)
            prop(CampaignReportEntity::startedMinions).isEqualTo(saved.startedMinions)
            prop(CampaignReportEntity::completedMinions).isEqualTo(saved.completedMinions)
            prop(CampaignReportEntity::successfulExecutions).isEqualTo(saved.successfulExecutions)
            prop(CampaignReportEntity::failedExecutions).isEqualTo(saved.failedExecutions)
            prop(CampaignReportEntity::scenariosReports).isEqualTo(saved.scenariosReports)
        }
    }

    @Test
    fun `should update the version when the campaign report is updated`() = testDispatcherProvider.run {
        // given
        val saved = campaignReportRepository.save(campaignReportPrototype.copy())

        // when
        val updated = campaignReportRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val saved = campaignReportRepository.save(campaignReportPrototype.copy())
        val scenarioReportPrototype = ScenarioReportEntity(
            name = "first",
            campaignReportId = saved.id,
            start = Instant.now().minusSeconds(900),
            end = Instant.now().minusSeconds(600),
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            status = ExecutionStatus.SUCCESSFUL
        )
        scenarioReportRepository.save(scenarioReportPrototype.copy())
        assertThat(scenarioReportRepository.findAll().count()).isEqualTo(1)
        assertThat(campaignReportRepository.findAll().count()).isEqualTo(1)

        // when
        campaignReportRepository.deleteById(saved.id)

        // then
        assertThat(scenarioReportRepository.findAll().count()).isEqualTo(0)
        assertThat(campaignReportRepository.findAll().count()).isEqualTo(0)
    }
}