package io.qalipsis.core.head.report

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
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
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    @MockK
    private lateinit var scenarioReportMessageRepository: ScenarioReportMessageRepository

    @InjectMockKs
    private lateinit var campaignReportProvider: DatabaseCampaignReportProvider

    @Test
    internal fun `should retrieve the right campaign report belonging to tenant`() = testDispatcherProvider.runTest {
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
        } returns listOf(
            CampaignReportEntity(
                id = 1,
                version = now,
                campaignId = 1,
                startedMinions = 5,
                completedMinions = 3,
                successfulExecutions = 3,
                failedExecutions = 2,
                status = ExecutionStatus.FAILED,
                scenariosReports = listOf(
                    ScenarioReportEntity(
                        id = 2,
                        version = now,
                        name = "scenario-1",
                        campaignReportId = 1,
                        start = now,
                        end = end,
                        startedMinions = 22,
                        completedMinions = 3,
                        successfulExecutions = 14,
                        failedExecutions = 13,
                        status = ExecutionStatus.SUCCESSFUL,
                        messages = emptyList()
                    ),
                    ScenarioReportEntity(
                        id = 3,
                        version = now,
                        name = "scenario-2",
                        campaignReportId = 1,
                        start = now.plusSeconds(2),
                        end = end.plusSeconds(3),
                        startedMinions = 22,
                        completedMinions = 13,
                        successfulExecutions = 11,
                        failedExecutions = 18,
                        status = ExecutionStatus.ABORTED,
                        messages = emptyList()
                    )
                )
            )
        )
        coEvery { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(2, 3)) } returns listOf(
            ScenarioReportMessageEntity(3, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error 1"),
            ScenarioReportMessageEntity(3, "step-2", "message-2", ReportMessageSeverity.INFO, "Info 1")
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
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.FAILED)
            prop(CampaignReport::scenariosReports).all {
                hasSize(2)
                index(0).all {
                    prop(ScenarioReport::campaignKey).isEqualTo("campaign-1")
                    prop(ScenarioReport::scenarioName).isEqualTo("scenario-1")
                    prop(ScenarioReport::start).isEqualTo(now)
                    prop(ScenarioReport::end).isEqualTo(end)
                    prop(ScenarioReport::startedMinions).isEqualTo(22)
                    prop(ScenarioReport::completedMinions).isEqualTo(3)
                    prop(ScenarioReport::successfulExecutions).isEqualTo(14)
                    prop(ScenarioReport::failedExecutions).isEqualTo(13)
                    prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                    prop(ScenarioReport::messages).isEmpty()
                }
                index(1).all {
                    prop(ScenarioReport::campaignKey).isEqualTo("campaign-1")
                    prop(ScenarioReport::scenarioName).isEqualTo("scenario-2")
                    prop(ScenarioReport::start).isEqualTo(now.plusSeconds(2))
                    prop(ScenarioReport::end).isEqualTo(end.plusSeconds(3))
                    prop(ScenarioReport::startedMinions).isEqualTo(22)
                    prop(ScenarioReport::completedMinions).isEqualTo(13)
                    prop(ScenarioReport::successfulExecutions).isEqualTo(11)
                    prop(ScenarioReport::failedExecutions).isEqualTo(18)
                    prop(ScenarioReport::status).isEqualTo(ExecutionStatus.ABORTED)
                    prop(ScenarioReport::messages).all {
                        hasSize(2)
                        index(0).all {
                            prop(ReportMessage::stepName).isEqualTo("step-1")
                            prop(ReportMessage::messageId).isEqualTo("message-1")
                            prop(ReportMessage::severity).isEqualTo(ReportMessageSeverity.ERROR)
                            prop(ReportMessage::message).isEqualTo("Error 1")
                        }
                        index(1).all {
                            prop(ReportMessage::stepName).isEqualTo("step-2")
                            prop(ReportMessage::messageId).isEqualTo("message-2")
                            prop(ReportMessage::severity).isEqualTo(ReportMessageSeverity.INFO)
                            prop(ReportMessage::message).isEqualTo("Info 1")
                        }
                    }
                }
            }
        }
    }

    @Test
    internal fun `should build the temporary campaign report belonging to tenant`() = testDispatcherProvider.runTest {
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
            result = ExecutionStatus.IN_PROGRESS,
            configurer = 3
        )
        coEvery { campaignReportRepository.findByCampaignId(any()) } returns emptyList()
        coEvery { campaignScenarioRepository.findByCampaignId(1) } returns listOf(
            CampaignScenarioEntity(1, "scenario-1", minionsCount = 1239),
            CampaignScenarioEntity(
                1,
                "scenario-2",
                start = now.plusMillis(1),
                end = now.plusSeconds(2),
                minionsCount = 2234
            )
        )

        // when
        val result = campaignReportProvider.retrieveCampaignReport(tenant = "my-tenant", campaignKey = "campaign-1")

        // then
        assertThat(result).all {
            prop(CampaignReport::campaignKey).isEqualTo("campaign-1")
            prop(CampaignReport::start).isEqualTo(now)
            prop(CampaignReport::end).isEqualTo(end)
            prop(CampaignReport::startedMinions).isNull()
            prop(CampaignReport::completedMinions).isNull()
            prop(CampaignReport::successfulExecutions).isNull()
            prop(CampaignReport::failedExecutions).isNull()
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.IN_PROGRESS)
            prop(CampaignReport::scenariosReports).all {
                hasSize(2)
                index(0).all {
                    prop(ScenarioReport::campaignKey).isEqualTo("campaign-1")
                    prop(ScenarioReport::scenarioName).isEqualTo("scenario-1")
                    prop(ScenarioReport::start).isNull()
                    prop(ScenarioReport::end).isNull()
                    prop(ScenarioReport::startedMinions).isNull()
                    prop(ScenarioReport::completedMinions).isNull()
                    prop(ScenarioReport::successfulExecutions).isNull()
                    prop(ScenarioReport::failedExecutions).isNull()
                    prop(ScenarioReport::status).isEqualTo(ExecutionStatus.QUEUED)
                    prop(ScenarioReport::messages).isEmpty()
                }
                index(1).all {
                    prop(ScenarioReport::campaignKey).isEqualTo("campaign-1")
                    prop(ScenarioReport::scenarioName).isEqualTo("scenario-2")
                    prop(ScenarioReport::start).isEqualTo(now.plusMillis(1))
                    prop(ScenarioReport::end).isNull()
                    prop(ScenarioReport::startedMinions).isNull()
                    prop(ScenarioReport::completedMinions).isNull()
                    prop(ScenarioReport::successfulExecutions).isNull()
                    prop(ScenarioReport::failedExecutions).isNull()
                    prop(ScenarioReport::status).isEqualTo(ExecutionStatus.IN_PROGRESS)
                    prop(ScenarioReport::messages).isEmpty()
                }
            }
        }
    }
}