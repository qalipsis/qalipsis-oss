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
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
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
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.ScenarioExecutionDetails
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
            campaignRepository.findByTenantAndKey("my-tenant", "campaign-1")
        } returns CampaignEntity(
            id = 1,
            version = now,
            tenantId = 4,
            key = "campaign-1",
            name = "my-campaign",
            scheduledMinions = 1,
            speedFactor = 1.0,
            start = now,
            end = end,
            result = ExecutionStatus.SUCCESSFUL,
            configurer = 3
        )
        coEvery {
            campaignReportRepository.findByCampaignId(1)
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
            prop(CampaignExecutionDetails::key).isEqualTo("campaign-1")
            prop(CampaignExecutionDetails::name).isEqualTo("my-campaign")
            prop(CampaignExecutionDetails::start).isEqualTo(now)
            prop(CampaignExecutionDetails::end).isEqualTo(end)
            prop(CampaignExecutionDetails::startedMinions).isEqualTo(5)
            prop(CampaignExecutionDetails::completedMinions).isEqualTo(3)
            prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(3)
            prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2)
            prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.FAILED)
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
        val now = Instant.now()
        val end = now.plusMillis(790976)
        // given
        coEvery {
            campaignRepository.findByTenantAndKey("my-tenant", "campaign-1")
        } returns CampaignEntity(
            id = 1,
            version = now,
            tenantId = 4,
            key = "campaign-1",
            name = "my-campaign",
            scheduledMinions = 1,
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
            prop(CampaignExecutionDetails::key).isEqualTo("campaign-1")
            prop(CampaignExecutionDetails::name).isEqualTo("my-campaign")
            prop(CampaignExecutionDetails::start).isEqualTo(now)
            prop(CampaignExecutionDetails::end).isEqualTo(end)
            prop(CampaignExecutionDetails::startedMinions).isNull()
            prop(CampaignExecutionDetails::completedMinions).isNull()
            prop(CampaignExecutionDetails::successfulExecutions).isNull()
            prop(CampaignExecutionDetails::failedExecutions).isNull()
            prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.IN_PROGRESS)
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