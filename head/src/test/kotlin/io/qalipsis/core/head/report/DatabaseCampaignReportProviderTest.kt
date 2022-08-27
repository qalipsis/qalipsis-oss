package io.qalipsis.core.head.report

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class DatabaseCampaignReportProviderTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var campaignReportRepository: CampaignReportRepository

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    @InjectMockKs
    private lateinit var campaignReportProvider: DatabaseCampaignReportProvider

    @Test
    internal fun `should retrieve the right campaign report belonging to tenant`() =
        testDispatcherProvider.runTest {
            val now = Instant.now()
            val end = now.plusMillis(790976)
            // given
            coEvery {
                campaignRepository.findByKey(
                    "my-tenant",
                    "campaign-1"
                )
            } returns CampaignEntity(
                id = 1,
                version = now,
                tenantId = 4,
                key = "campaign-1",
                name = "my-campaign",
                speedFactor = 1.0,
                start = now,
                end = end,
                result = ExecutionStatus.SUCCESSFUL,
                configurer = 3
            )
            coEvery {
                campaignReportRepository.findByCampaignId(
                    1
                )
            } returns CampaignReportEntity(
                id = 1,
                version = now,
                campaignId = 1,
                startedMinions = 5,
                completedMinions = 3,
                successfulExecutions = 3,
                failedExecutions = 2,
                scenariosReports = listOf(
                    ScenarioReportEntity(
                        id = 2,
                        version = now,
                        name = "scenario-1",
                        campaignReportId = 1,
                        start = now,
                        end = end,
                        startedMinions = 2,
                        completedMinions = 1,
                        successfulExecutions = 1,
                        failedExecutions = 1,
                        status = ExecutionStatus.SUCCESSFUL,
                        messages = emptyList()
                    )
                )
            )

            // when
            val result = campaignReportProvider.retrieveCampaignReport(tenant = "my-tenant", campaignKey = "campaign-1")

            // then
            assertThat(result).all {
                prop(CampaignReport::campaignKey).isEqualTo("campaign-1")
                prop(CampaignReport::start).isEqualTo(now)
                prop(CampaignReport::end).isEqualTo(end)
                prop(CampaignReport::startedMinions).isEqualTo(5)
                prop(CampaignReport::completedMinions).isEqualTo(3)
                prop(CampaignReport::successfulExecutions).isEqualTo(3)
                prop(CampaignReport::failedExecutions).isEqualTo(2)
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                prop(CampaignReport::scenariosReports).hasSize(1)
            }
        }
}