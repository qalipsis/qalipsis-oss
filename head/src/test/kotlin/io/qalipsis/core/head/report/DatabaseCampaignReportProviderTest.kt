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

package io.qalipsis.core.head.report

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.core.head.model.converter.CampaignConverter
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

    @MockK
    private lateinit var campaignConverter: CampaignConverter

    @InjectMockKs
    private lateinit var campaignReportProvider: DatabaseCampaignReportProvider

    @Test
    internal fun `should retrieve the right campaign report belonging to tenant`() = testDispatcherProvider.runTest {
        // given
        val now = Instant.now()
        val creation = Instant.now().minusMillis(123)
        val start = Instant.now().minusMillis(12)
        val end = start.plusMillis(790976)
        val campaignEntity = mockk<CampaignEntity> {
            every { id } returns 342
        }
        coEvery {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
        } returns campaignEntity
        val scenario1 = mockk<Scenario>()
        val scenario2 = mockk<Scenario>()
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns Campaign(
            creation = creation,
            version = now,
            key = "my-campaign",
            name = "This is a campaign",
            speedFactor = 123.62,
            scheduledMinions = 123,
            hardTimeout = end.plusSeconds(1),
            start = start,
            end = end,
            status = ExecutionStatus.SUCCESSFUL,
            failureReason = "The failure",
            configurerName = "my-user",
            aborterName = null,
            scenarios = listOf(scenario1, scenario2),
            zones = setOf("zone-1", "zone-2")
        )
        coEvery {
            campaignReportRepository.findByCampaignId(342)
        } returns CampaignReportEntity(
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
        coEvery { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(2, 3)) } returns listOf(
            ScenarioReportMessageEntity(3, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error 1"),
            ScenarioReportMessageEntity(3, "step-2", "message-2", ReportMessageSeverity.INFO, "Info 1")
        )

        // when
        val result = campaignReportProvider.retrieveCampaignReport(tenant = "my-tenant", campaignKey = "my-campaign")

        // then
        assertThat(result).all {
            prop(CampaignExecutionDetails::creation).isEqualTo(creation)
            prop(CampaignExecutionDetails::version).isEqualTo(now)
            prop(CampaignExecutionDetails::key).isEqualTo("my-campaign")
            prop(CampaignExecutionDetails::name).isEqualTo("This is a campaign")
            prop(CampaignExecutionDetails::start).isEqualTo(start)
            prop(CampaignExecutionDetails::end).isEqualTo(end)
            prop(CampaignExecutionDetails::speedFactor).isEqualTo(123.62)
            prop(CampaignExecutionDetails::hardTimeout).isEqualTo(end.plusSeconds(1))
            prop(CampaignExecutionDetails::startedMinions).isEqualTo(5)
            prop(CampaignExecutionDetails::configurerName).isEqualTo("my-user")
            prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(3)
            prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2)
            prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.FAILED)
            prop(CampaignExecutionDetails::failureReason).isEqualTo("The failure")
            prop(CampaignExecutionDetails::scenarios).isEqualTo(listOf(scenario1, scenario2))
            prop(CampaignExecutionDetails::zones).containsExactlyInAnyOrder("zone-1", "zone-2")
            prop(CampaignExecutionDetails::scenariosReports).all {
                hasSize(2)
                index(0).all {
                    prop(ScenarioExecutionDetails::id).isEqualTo("scenario-1")
                    prop(ScenarioExecutionDetails::name).isEqualTo("scenario-1")
                    prop(ScenarioExecutionDetails::start).isEqualTo(now)
                    prop(ScenarioExecutionDetails::end).isEqualTo(end)
                    prop(ScenarioExecutionDetails::startedMinions).isEqualTo(22)
                    prop(ScenarioExecutionDetails::completedMinions).isEqualTo(3)
                    prop(ScenarioExecutionDetails::successfulExecutions).isEqualTo(14)
                    prop(ScenarioExecutionDetails::failedExecutions).isEqualTo(13)
                    prop(ScenarioExecutionDetails::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                    prop(ScenarioExecutionDetails::messages).isEmpty()
                }
                index(1).all {
                    prop(ScenarioExecutionDetails::id).isEqualTo("scenario-2")
                    prop(ScenarioExecutionDetails::name).isEqualTo("scenario-2")
                    prop(ScenarioExecutionDetails::start).isEqualTo(now.plusSeconds(2))
                    prop(ScenarioExecutionDetails::end).isEqualTo(end.plusSeconds(3))
                    prop(ScenarioExecutionDetails::startedMinions).isEqualTo(22)
                    prop(ScenarioExecutionDetails::completedMinions).isEqualTo(13)
                    prop(ScenarioExecutionDetails::successfulExecutions).isEqualTo(11)
                    prop(ScenarioExecutionDetails::failedExecutions).isEqualTo(18)
                    prop(ScenarioExecutionDetails::status).isEqualTo(ExecutionStatus.ABORTED)
                    prop(ScenarioExecutionDetails::messages).all {
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
        // given
        val now = Instant.now()
        val creation = Instant.now().minusMillis(123)
        val start = Instant.now().minusMillis(12)
        val end = start.plusMillis(790976)
        val campaignEntity = mockk<CampaignEntity> {
            every { id } returns 342
        }
        coEvery {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
        } returns campaignEntity
        val scenario1 = mockk<Scenario>()
        val scenario2 = mockk<Scenario>()
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns Campaign(
            creation = creation,
            version = now,
            key = "my-campaign",
            name = "This is a campaign",
            speedFactor = 123.62,
            scheduledMinions = 123,
            hardTimeout = end.plusSeconds(1),
            start = start,
            end = end,
            status = ExecutionStatus.IN_PROGRESS,
            configurerName = "my-user",
            aborterName = null,
            scenarios = listOf(scenario1, scenario2),
        )
        coEvery { campaignReportRepository.findByCampaignId(342) } returns null
        coEvery { campaignScenarioRepository.findByCampaignId(342) } returns listOf(
            CampaignScenarioEntity(1, "scenario-1", minionsCount = 1239),
            CampaignScenarioEntity(
                342,
                "scenario-2",
                start = now.plusMillis(1),
                end = now.plusSeconds(2),
                minionsCount = 2234
            )
        )

        // when
        val result = campaignReportProvider.retrieveCampaignReport(tenant = "my-tenant", campaignKey = "my-campaign")

        // then
        assertThat(result).all {
            prop(CampaignExecutionDetails::creation).isEqualTo(creation)
            prop(CampaignExecutionDetails::version).isEqualTo(now)
            prop(CampaignExecutionDetails::key).isEqualTo("my-campaign")
            prop(CampaignExecutionDetails::name).isEqualTo("This is a campaign")
            prop(CampaignExecutionDetails::start).isEqualTo(start)
            prop(CampaignExecutionDetails::end).isEqualTo(end)
            prop(CampaignExecutionDetails::speedFactor).isEqualTo(123.62)
            prop(CampaignExecutionDetails::hardTimeout).isEqualTo(end.plusSeconds(1))
            prop(CampaignExecutionDetails::startedMinions).isNull()
            prop(CampaignExecutionDetails::completedMinions).isNull()
            prop(CampaignExecutionDetails::successfulExecutions).isNull()
            prop(CampaignExecutionDetails::failedExecutions).isNull()
            prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.IN_PROGRESS)
            prop(CampaignExecutionDetails::failureReason).isNull()
            prop(CampaignExecutionDetails::scenarios).isEqualTo(listOf(scenario1, scenario2))
            prop(CampaignExecutionDetails::zones).isEmpty()
            prop(CampaignExecutionDetails::scenariosReports).all {
                hasSize(2)
                index(0).all {
                    prop(ScenarioExecutionDetails::id).isEqualTo("scenario-1")
                    prop(ScenarioExecutionDetails::name).isEqualTo("scenario-1")
                    prop(ScenarioExecutionDetails::start).isNull()
                    prop(ScenarioExecutionDetails::end).isNull()
                    prop(ScenarioExecutionDetails::startedMinions).isNull()
                    prop(ScenarioExecutionDetails::completedMinions).isNull()
                    prop(ScenarioExecutionDetails::successfulExecutions).isNull()
                    prop(ScenarioExecutionDetails::failedExecutions).isNull()
                    prop(ScenarioExecutionDetails::status).isEqualTo(ExecutionStatus.QUEUED)
                    prop(ScenarioExecutionDetails::messages).isEmpty()
                }
                index(1).all {
                    prop(ScenarioExecutionDetails::id).isEqualTo("scenario-2")
                    prop(ScenarioExecutionDetails::name).isEqualTo("scenario-2")
                    prop(ScenarioExecutionDetails::start).isEqualTo(now.plusMillis(1))
                    prop(ScenarioExecutionDetails::end).isNull()
                    prop(ScenarioExecutionDetails::startedMinions).isNull()
                    prop(ScenarioExecutionDetails::completedMinions).isNull()
                    prop(ScenarioExecutionDetails::successfulExecutions).isNull()
                    prop(ScenarioExecutionDetails::failedExecutions).isNull()
                    prop(ScenarioExecutionDetails::status).isEqualTo(ExecutionStatus.IN_PROGRESS)
                    prop(ScenarioExecutionDetails::messages).isEmpty()
                }
            }
        }
    }
}