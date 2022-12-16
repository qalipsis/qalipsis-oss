/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportRepository
import io.qalipsis.core.head.report.DatabaseCampaignReportPublisher
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
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
    internal fun `should save the new campaign report`() = testDispatcherProvider.run {
        // given
        val now = getTimeMock()
        val campaignEntity = CampaignEntity(
            key = "my-campaign",
            name = "The campaign",
            creation = now.minusSeconds(12),
            configurer = 123L,
            scheduledMinions = 123
        ).copy(id = 8)
        coEvery { campaignRepository.findByKey("my-campaign") } returns campaignEntity
        coEvery { campaignRepository.update(any()) } returnsArgument 0
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
            campaignKey = "my-campaign",
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
            campaignKey = "my-campaign",
            start = now.minusSeconds(1000),
            end = now.minusSeconds(500),
            scheduledMinions = 1,
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            status = ExecutionStatus.FAILED,
            scenariosReports = mutableListOf(scenarioReport)
        )

        // when
        databaseCampaignReportPublisher.publish("my-campaign", campaignReport)

        // then
        coVerifyOrder {
            campaignRepository.findByKey("my-campaign")
            campaignRepository.update(
                CampaignEntity(
                    key = "my-campaign",
                    name = "The campaign",
                    creation = now.minusSeconds(12),
                    configurer = 123L,
                    scheduledMinions = 123,
                    end = now.minusSeconds(500),
                    result = ExecutionStatus.FAILED
                ).copy(id = 8)
            )
            campaignReportRepository.save(
                CampaignReportEntity(
                    campaignId = 8,
                    startedMinions = 1000,
                    completedMinions = 990,
                    successfulExecutions = 990,
                    failedExecutions = 10,
                    status = ExecutionStatus.FAILED
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
        confirmVerified(
            campaignRepository,
            campaignReportRepository,
            scenarioReportRepository,
            scenarioReportMessageRepository
        )
    }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }
}