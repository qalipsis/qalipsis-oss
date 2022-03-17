package io.qalipsis.core.head.campaign

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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@WithMockk
internal class PersistentCampaignReportServiceTest {

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
    private lateinit var persistentCampaignReportService: PersistentCampaignReportService

    @Test
    internal fun `should save the new campaign`() = testDispatcherProvider.run {
        // given
        val now = getTimeMock()
        coEvery { campaignRepository.findIdByName("my-campaign") } returns 8
        val mockedSavedScenarioReport = mockk<ScenarioReportEntity>(relaxed = true) {
            every { id } returns 10
            every { messages = any() } returns Unit
        }
        coEvery { scenarioReportRepository.save(any()) } returns mockedSavedScenarioReport
        val mockedSavedCampaignReport = mockk<CampaignReportEntity>(relaxed = true) {
            every { id } returns 9
            every { scenariosReports = any() } returns Unit
        }
        coEvery { campaignReportRepository.save(any()) } returns mockedSavedCampaignReport

        val messageOne = ReportMessage(
            stepId = "my-step",
            messageId = "my-message-1",
            severity = ReportMessageSeverity.INFO,
            message = "This is the first message"
        )
        val messageTwo = ReportMessage(
            stepId = "my-step",
            messageId = "my-message-2",
            severity = ReportMessageSeverity.ERROR,
            message = "This is the second message"
        )
        val messages = mutableListOf(messageOne, messageTwo)

        val scenarioReport = ScenarioReport(
            campaignId = "my-campaign",
            scenarioId = "my-scenario",
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
            campaignId = "my-campaign",
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
        persistentCampaignReportService.save(campaignReport)

        // then
        coVerifyOrder {
            campaignReportRepository.save(
                CampaignReportEntity(8, 1000, 990, 990, 10)
            )
            scenarioReportRepository.save(
                ScenarioReportEntity(
                    9,
                    now.minusSeconds(900),
                    now.minusSeconds(600),
                    1000, 990, 990, 10,
                    ExecutionStatus.SUCCESSFUL
                )
            )
            scenarioReportMessageRepository.saveAll(
                listOf(
                    ScenarioReportMessageEntity(
                        10,
                        "my-step",
                        "my-message-1",
                        ReportMessageSeverity.INFO,
                        "This is the first message"
                    ),
                    ScenarioReportMessageEntity(
                        10,
                        "my-step",
                        "my-message-2",
                        ReportMessageSeverity.ERROR,
                        "This is the second message"
                    )
                )
            )
            scenarioReportRepository.update(mockedSavedScenarioReport)
            campaignReportRepository.update(mockedSavedCampaignReport)
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