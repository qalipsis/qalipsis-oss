package io.qalipsis.core.report

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportRepository
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@WithMockk
internal class DatabaseCampaignReportPublisherTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignRepository: CampaignRepository

    @RelaxedMockK
    private lateinit var campaignReportRepository: CampaignReportRepository

    @RelaxedMockK
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @RelaxedMockK
    private lateinit var scenarioReportMessageRepository: ScenarioReportMessageRepository

    @InjectMockKs
    private lateinit var databaseCampaignReportPublisher: DatabaseCampaignReportPublisher

    @Test
    internal fun `should save the new campaign`() = testDispatcherProvider.run {
        // given
        val now = getTimeMock()
        coEvery { campaignRepository.findIdByName("my-campaign") } returns 8
        val mockedSavedScenarioReport = mockk<ScenarioReportEntity>(relaxed = true) {
            every { id } returns 10L
            every { name } returns "my-scenario"
        }
        coEvery { scenarioReportRepository.saveAll(any<Iterable<ScenarioReportEntity>>()) } returns flowOf(
            mockedSavedScenarioReport
        )
        val mockedSavedCampaignReport = mockk<CampaignReportEntity>(relaxed = true) {
            every { id } returns 9
        }
        coEvery { campaignReportRepository.save(any()) } returns mockedSavedCampaignReport


        val messageOne = ReportMessage(
            stepName = "my-step",
            messageId = "my-message-1",
            severity = ReportMessageSeverity.INFO,
            message = "This is the first message"
        )
        val messageTwo = ReportMessage(
            stepName = "my-step",
            messageId = "my-message-2",
            severity = ReportMessageSeverity.ERROR,
            message = "This is the second message"
        )
        val messages = mutableListOf(messageOne, messageTwo)

        val scenarioReport = ScenarioReport(
            campaignName = "my-campaign",
            scenarioName = "my-scenario",
            start = now.minusSeconds(900),
            end = now.minusSeconds(600),
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            status = ExecutionStatus.SUCCESSFUL,
            messages = messages
        )
        val campaignReport = CampaignReport(
            campaignName = "my-campaign",
            start = now.minusSeconds(1000),
            end = now.minusSeconds(500),
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = mutableListOf(scenarioReport)
        )

        // when
        databaseCampaignReportPublisher.publish(relaxedMockk(), campaignReport)

        // then
        coVerifyOrder {
            campaignReportRepository.save(
                CampaignReportEntity(
                    campaignId = 8,
                    startedMinions = 1000,
                    completedMinions = 990,
                    successfulExecutions = 990,
                    failedExecutions = 10
                )
            )
            scenarioReportRepository.saveAll(
                listOf(
                    ScenarioReportEntity(
                        name = "my-scenario",
                        campaignReportId = 9,
                        start = now.minusSeconds(900),
                        end = now.minusSeconds(600),
                        startedMinions = 1000,
                        completedMinions = 990,
                        successfulExecutions = 990,
                        failedExecutions = 10,
                        status = ExecutionStatus.SUCCESSFUL
                    )
                )
            )
            scenarioReportMessageRepository.saveAll(
                listOf(
                    ScenarioReportMessageEntity(
                        scenarioReportId = 10,
                        stepName = "my-step",
                        messageId = "my-message-1",
                        severity = ReportMessageSeverity.INFO,
                        message = "This is the first message"
                    ),
                    ScenarioReportMessageEntity(
                        scenarioReportId = 10,
                        stepName = "my-step",
                        messageId = "my-message-2",
                        severity = ReportMessageSeverity.ERROR,
                        message = "This is the second message"
                    )
                )
            )
        }
        confirmVerified(campaignReportRepository, scenarioReportRepository, scenarioReportMessageRepository)
    }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }
}