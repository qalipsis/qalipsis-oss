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
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.spyk
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class DatabaseCampaignReportProviderTest {

    @field:RegisterExtension
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

    private lateinit var campaignReportProvider: DatabaseCampaignReportProvider

    @BeforeEach
    internal fun setUp() {
        campaignReportProvider = spyk(
            DatabaseCampaignReportProvider(
                campaignRepository = campaignRepository,
                campaignConverter = campaignConverter,
                campaignScenarioRepository = campaignScenarioRepository,
                campaignReportRepository = campaignReportRepository,
                scenarioReportMessageRepository = scenarioReportMessageRepository
            ),
            recordPrivateCalls = true
        )
    }

    @Test
    internal fun `should map a list of one scenario report messages to the corresponding list of report messages`() =
        testDispatcherProvider.runTest {
            // given
            val scenarioReportMessages = listOf(
                ScenarioReportMessageEntity(3, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error")
            )

            // when
            val result = campaignReportProvider.coInvokeInvisible<List<ReportMessage>>(
                "mapScenarioReportMessageEntity",
                scenarioReportMessages
            )

            // then
            assertThat(result).isNotNull().all {
                hasSize(1)
                isEqualTo(
                    listOf(
                        ReportMessage(
                            stepName = "step-1",
                            messageId = "message-1",
                            severity = ReportMessageSeverity.ERROR,
                            message = "Error"
                        )
                    )
                )
            }

            confirmVerified(
                campaignRepository,
                campaignConverter,
                campaignScenarioRepository,
                campaignReportRepository,
                scenarioReportMessageRepository
            )
        }

    @Test
    internal fun `should map a list of scenario report messages to the corresponding list of report messages`() =
        testDispatcherProvider.runTest {
            // given
            val scenarioReportMessages = listOf(
                ScenarioReportMessageEntity(3, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error"),
                ScenarioReportMessageEntity(3, "step-2", "message-2", ReportMessageSeverity.INFO, "Info"),
                ScenarioReportMessageEntity(3, "step-4", "message-4", ReportMessageSeverity.WARN, "Warn"),
                ScenarioReportMessageEntity(3, "step-3", "message-3", ReportMessageSeverity.ABORT, "Abort")
            )

            // when
            val result = campaignReportProvider.coInvokeInvisible<List<ReportMessage>>(
                "mapScenarioReportMessageEntity",
                scenarioReportMessages
            )

            // then
            assertThat(result).isNotNull().all {
                hasSize(4)
                isEqualTo(
                    listOf(
                        ReportMessage(
                            stepName = "step-1",
                            messageId = "message-1",
                            severity = ReportMessageSeverity.ERROR,
                            message = "Error"
                        ),
                        ReportMessage(
                            stepName = "step-2",
                            messageId = "message-2",
                            severity = ReportMessageSeverity.INFO,
                            message = "Info"
                        ),
                        ReportMessage(
                            stepName = "step-4",
                            messageId = "message-4",
                            severity = ReportMessageSeverity.WARN,
                            message = "Warn"
                        ),
                        ReportMessage(
                            stepName = "step-3",
                            messageId = "message-3",
                            severity = ReportMessageSeverity.ABORT,
                            message = "Abort"
                        )
                    )
                )
            }
        }

    @Test
    internal fun `should map a list of one scenario report to the corresponding list of scenario execution details`() =
        testDispatcherProvider.runTest {
            // given
            val now = Instant.now()
            val end = Instant.now().plusMillis(790976)
            val scenarioReportEntities = listOf(
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
                )
            )
            coEvery { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(2)) } returns listOf(
                ScenarioReportMessageEntity(2, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error")
            )
            every {
                campaignReportProvider["mapScenarioReportMessageEntity"](
                    listOf(
                        ScenarioReportMessageEntity(2, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error")
                    )
                )
            } returns listOf(
                ReportMessage(
                    stepName = "step-1",
                    messageId = "message-1",
                    severity = ReportMessageSeverity.ERROR,
                    message = "Error"
                )
            )

            // when
            val result = campaignReportProvider.coInvokeInvisible<List<ScenarioExecutionDetails>>(
                "mapScenarioReport",
                scenarioReportEntities
            )

            // then
            assertThat(result).isNotNull().all {
                hasSize(1)
                isEqualTo(
                    listOf(
                        ScenarioExecutionDetails(
                            id = "scenario-1",
                            name = "scenario-1",
                            start = now,
                            end = end,
                            startedMinions = 22,
                            completedMinions = 3,
                            successfulExecutions = 14,
                            failedExecutions = 13,
                            status = ExecutionStatus.SUCCESSFUL,
                            messages = listOf(
                                ReportMessage(
                                    stepName = "step-1",
                                    messageId = "message-1",
                                    severity = ReportMessageSeverity.ERROR,
                                    message = "Error"
                                )
                            )
                        )
                    )
                )
            }
            coVerifyOrder {
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(2))
                campaignReportProvider["mapScenarioReportMessageEntity"](
                    listOf(
                        ScenarioReportMessageEntity(2, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error")
                    )
                )
            }

            confirmVerified(
                campaignRepository,
                campaignConverter,
                campaignScenarioRepository,
                campaignReportRepository,
                scenarioReportMessageRepository
            )
        }

    @Test
    internal fun `should map a list of scenario reports to the corresponding list of scenario execution details`() =
        testDispatcherProvider.runTest {
            // given
            val now = Instant.now()
            val end = Instant.now().plusMillis(790976)
            val scenarioReportEntities = listOf(
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
            coEvery { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(2, 3)) } returns listOf(
                ScenarioReportMessageEntity(2, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error"),
                ScenarioReportMessageEntity(3, "step-3", "message-3", ReportMessageSeverity.INFO, "Info"),
                ScenarioReportMessageEntity(2, "step-2", "message-2", ReportMessageSeverity.INFO, "Info"),
                ScenarioReportMessageEntity(3, "step-4", "message-4", ReportMessageSeverity.ABORT, "Abort"),
                ScenarioReportMessageEntity(3, "step-5", "message-5", ReportMessageSeverity.WARN, "Warn")
            )
            every {
                campaignReportProvider["mapScenarioReportMessageEntity"](
                    listOf(
                        ScenarioReportMessageEntity(2, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error"),
                        ScenarioReportMessageEntity(2, "step-2", "message-2", ReportMessageSeverity.INFO, "Info")
                    )
                )
            } returns listOf(
                ReportMessage(
                    stepName = "step-1",
                    messageId = "message-1",
                    severity = ReportMessageSeverity.ERROR,
                    message = "Error"
                ),
                ReportMessage(
                    stepName = "step-2",
                    messageId = "message-2",
                    severity = ReportMessageSeverity.INFO,
                    message = "Info"
                )
            )
            every {
                campaignReportProvider["mapScenarioReportMessageEntity"](
                    listOf(
                        ScenarioReportMessageEntity(3, "step-3", "message-3", ReportMessageSeverity.INFO, "Info"),
                        ScenarioReportMessageEntity(3, "step-4", "message-4", ReportMessageSeverity.ABORT, "Abort"),
                        ScenarioReportMessageEntity(3, "step-5", "message-5", ReportMessageSeverity.WARN, "Warn")
                    )
                )
            } returns listOf(
                ReportMessage(
                    stepName = "step-3",
                    messageId = "message-3",
                    severity = ReportMessageSeverity.INFO,
                    message = "Info"
                ),
                ReportMessage(
                    stepName = "step-4",
                    messageId = "message-4",
                    severity = ReportMessageSeverity.ABORT,
                    message = "Abort"
                ),
                ReportMessage(
                    stepName = "step-5",
                    messageId = "message-5",
                    severity = ReportMessageSeverity.WARN,
                    message = "Warn"
                )
            )

            // when
            val result = campaignReportProvider.coInvokeInvisible<List<ScenarioExecutionDetails>>(
                "mapScenarioReport",
                scenarioReportEntities
            )

            // then
            assertThat(result).isNotNull().all {
                hasSize(2)
                isEqualTo(
                    listOf(
                        ScenarioExecutionDetails(
                            id = "scenario-1",
                            name = "scenario-1",
                            start = now,
                            end = end,
                            startedMinions = 22,
                            completedMinions = 3,
                            successfulExecutions = 14,
                            failedExecutions = 13,
                            status = ExecutionStatus.SUCCESSFUL,
                            messages = listOf(
                                ReportMessage(
                                    stepName = "step-1",
                                    messageId = "message-1",
                                    severity = ReportMessageSeverity.ERROR,
                                    message = "Error"
                                ),
                                ReportMessage(
                                    stepName = "step-2",
                                    messageId = "message-2",
                                    severity = ReportMessageSeverity.INFO,
                                    message = "Info"
                                )
                            )
                        ),
                        ScenarioExecutionDetails(
                            id = "scenario-2",
                            name = "scenario-2",
                            start = now.plusSeconds(2),
                            end = end.plusSeconds(3),
                            startedMinions = 22,
                            completedMinions = 13,
                            successfulExecutions = 11,
                            failedExecutions = 18,
                            status = ExecutionStatus.ABORTED,
                            messages = listOf(
                                ReportMessage(
                                    stepName = "step-3",
                                    messageId = "message-3",
                                    severity = ReportMessageSeverity.INFO,
                                    message = "Info"
                                ),
                                ReportMessage(
                                    stepName = "step-4",
                                    messageId = "message-4",
                                    severity = ReportMessageSeverity.ABORT,
                                    message = "Abort"
                                ),
                                ReportMessage(
                                    stepName = "step-5",
                                    messageId = "message-5",
                                    severity = ReportMessageSeverity.WARN,
                                    message = "Warn"
                                )
                            )
                        )
                    )
                )
            }
            coVerifyOrder {
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(2, 3))
                campaignReportProvider["mapScenarioReportMessageEntity"](
                    listOf(
                        ScenarioReportMessageEntity(2, "step-1", "message-1", ReportMessageSeverity.ERROR, "Error"),
                        ScenarioReportMessageEntity(2, "step-2", "message-2", ReportMessageSeverity.INFO, "Info")
                    )
                )
                campaignReportProvider["mapScenarioReportMessageEntity"](
                    listOf(
                        ScenarioReportMessageEntity(3, "step-3", "message-3", ReportMessageSeverity.INFO, "Info"),
                        ScenarioReportMessageEntity(3, "step-4", "message-4", ReportMessageSeverity.ABORT, "Abort"),
                        ScenarioReportMessageEntity(3, "step-5", "message-5", ReportMessageSeverity.WARN, "Warn")
                    )
                )
            }

            confirmVerified(
                campaignRepository,
                campaignConverter,
                campaignScenarioRepository,
                campaignReportRepository,
                scenarioReportMessageRepository
            )
        }

    @Test
    internal fun `should provide default values for a scenario when the campaign is ongoing`() =
        testDispatcherProvider.runTest {
            // given
            val now = Instant.now()
            val campaignScenarios = listOf(
                CampaignScenarioEntity(
                    id = 1,
                    version = now,
                    campaignId = 2,
                    name = "scenario-1",
                    minionsCount = 5,
                    start = now,
                    end = null
                )
            )
            coEvery { campaignScenarioRepository.findByCampaignId(2) } returns campaignScenarios

            // when
            val result = campaignReportProvider.coInvokeInvisible<List<ScenarioExecutionDetails>>(
                "ongoingScenariosDetails",
                2
            )

            // then
            assertThat(result).isNotNull().all {
                hasSize(1)
                isEqualTo(
                    listOf(
                        ScenarioExecutionDetails(
                            id = "scenario-1",
                            name = "scenario-1",
                            start = now,
                            end = null,
                            startedMinions = null,
                            completedMinions = null,
                            successfulExecutions = null,
                            failedExecutions = null,
                            status = ExecutionStatus.IN_PROGRESS,
                            messages = listOf()
                        )
                    )
                )
            }
            coVerifyOrder {
                campaignScenarioRepository.findByCampaignId(2)
            }

            confirmVerified(
                campaignRepository,
                campaignConverter,
                campaignScenarioRepository,
                campaignReportRepository,
                scenarioReportMessageRepository
            )
        }

    @Test
    internal fun `should provide default values for a list of scenarios when the campaign is ongoing`() =
        testDispatcherProvider.runTest {
            // given
            val now = Instant.now()
            val campaignScenarios = listOf(
                CampaignScenarioEntity(
                    id = 1,
                    version = now,
                    campaignId = 2,
                    name = "scenario-1",
                    minionsCount = 5,
                    start = now,
                    end = null
                ),
                CampaignScenarioEntity(
                    id = 2,
                    version = now,
                    campaignId = 2,
                    name = "scenario-2",
                    minionsCount = 5,
                    start = null,
                    end = null
                ),
                CampaignScenarioEntity(
                    id = 3,
                    version = now,
                    campaignId = 2,
                    name = "scenario-3",
                    minionsCount = 15,
                    start = now,
                    end = null
                )
            )
            coEvery { campaignScenarioRepository.findByCampaignId(2) } returns campaignScenarios

            // when
            val result = campaignReportProvider.coInvokeInvisible<List<ScenarioExecutionDetails>>(
                "ongoingScenariosDetails",
                2
            )

            // then
            assertThat(result).isNotNull().all {
                hasSize(3)
                isEqualTo(
                    listOf(
                        ScenarioExecutionDetails(
                            id = "scenario-1",
                            name = "scenario-1",
                            start = now,
                            end = null,
                            startedMinions = null,
                            completedMinions = null,
                            successfulExecutions = null,
                            failedExecutions = null,
                            status = ExecutionStatus.IN_PROGRESS,
                            messages = listOf()
                        ),
                        ScenarioExecutionDetails(
                            id = "scenario-2",
                            name = "scenario-2",
                            start = null,
                            end = null,
                            startedMinions = null,
                            completedMinions = null,
                            successfulExecutions = null,
                            failedExecutions = null,
                            status = ExecutionStatus.QUEUED,
                            messages = listOf()
                        ),
                        ScenarioExecutionDetails(
                            id = "scenario-3",
                            name = "scenario-3",
                            start = now,
                            end = null,
                            startedMinions = null,
                            completedMinions = null,
                            successfulExecutions = null,
                            failedExecutions = null,
                            status = ExecutionStatus.IN_PROGRESS,
                            messages = listOf()
                        )
                    )
                )
            }
            coVerifyOrder {
                campaignScenarioRepository.findByCampaignId(2)
            }

            confirmVerified(
                campaignRepository,
                campaignConverter,
                campaignScenarioRepository,
                campaignReportRepository,
                scenarioReportMessageRepository
            )
        }

    @Test
    internal fun `should retrieve a default campaign report in a tenant`() = testDispatcherProvider.runTest {
        // given
        val now = Instant.now()
        val creation = Instant.now().minusMillis(123)
        val campaignEntity = mockk<CampaignEntity> {
            every { id } returns 3
        }
        coEvery {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1"))
        } returns listOf(campaignEntity)
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns Campaign(
            version = now,
            key = "key-1",
            creation = creation,
            name = "This is a campaign",
            speedFactor = 123.62,
            scheduledMinions = null,
            start = null,
            end = null,
            status = ExecutionStatus.SUCCESSFUL,
            configurerName = null,
            scenarios = listOf()
        )
        coEvery {
            campaignReportRepository.findByCampaignId(3)
        } returns null
        coEvery {
            campaignReportProvider["ongoingScenariosDetails"](3L)
        } returns listOf<ScenarioExecutionDetails>()

        // when
        val result = campaignReportProvider.retrieveCampaignsReports(tenant = "my-tenant", campaignKeys = listOf("key-1"))

        // then
        assertThat(result).all {
            hasSize(1)
            isEqualTo(
                listOf(
                    CampaignExecutionDetails(
                        version = now,
                        key = "key-1",
                        creation = creation,
                        name = "This is a campaign",
                        speedFactor = 123.62,
                        scheduledMinions = null,
                        start = null,
                        end = null,
                        status = ExecutionStatus.SUCCESSFUL,
                        scenarios = listOf(),
                        startedMinions = null,
                        completedMinions = null,
                        successfulExecutions = null,
                        failedExecutions = null,
                        scenariosReports = listOf()
                    )
                )
            )
        }
        coVerifyOrder {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1"))
            campaignConverter.convertToModel(refEq(campaignEntity))
            campaignReportRepository.findByCampaignId(3)
            campaignReportProvider["ongoingScenariosDetails"](3L)
        }

        confirmVerified(
            campaignRepository,
            campaignConverter,
            campaignScenarioRepository,
            campaignReportRepository,
            scenarioReportMessageRepository
        )
    }

    @Test
    internal fun `should retrieve a minimal campaign report in a tenant`() = testDispatcherProvider.runTest {
        // given
        val now = Instant.now()
        val creation = Instant.now().minusMillis(123)
        val start = Instant.now().minusMillis(12)
        val end = start.plusMillis(790976)
        val scenario1 = mockk<Scenario>()
        val scenario2 = mockk<Scenario>()
        val campaignEntity = mockk<CampaignEntity> {
            every { id } returns 2
        }
        coEvery {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1"))
        } returns listOf(campaignEntity)
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns Campaign(
            version = now,
            key = "key-1",
            creation = creation,
            name = "This is a campaign",
            speedFactor = 123.62,
            scheduledMinions = 1,
            start = start,
            end = end,
            status = ExecutionStatus.SUCCESSFUL,
            configurerName = null,
            scenarios = listOf(scenario1, scenario2),
            zones = setOf()
        )
        coEvery {
            campaignReportRepository.findByCampaignId(2)
        } returns CampaignReportEntity(
            id = 1,
            version = now,
            campaignId = 2,
            startedMinions = 5,
            completedMinions = 3,
            successfulExecutions = 3,
            failedExecutions = 2,
            status = ExecutionStatus.FAILED,
            scenariosReports = listOf()
        )
        coEvery {
            campaignReportProvider["mapScenarioReport"](listOf<ScenarioReportEntity>())
        } returns listOf<ScenarioExecutionDetails>()

        // when
        val result = campaignReportProvider.retrieveCampaignsReports(tenant = "my-tenant", campaignKeys = listOf("key-1"))

        // then
        assertThat(result).all {
            hasSize(1)
            isEqualTo(
                listOf(
                    CampaignExecutionDetails(
                        version = now,
                        key = "key-1",
                        creation = creation,
                        name = "This is a campaign",
                        speedFactor = 123.62,
                        scheduledMinions = 1,
                        start = start,
                        end = end,
                        status = ExecutionStatus.FAILED,
                        scenarios = listOf(scenario1, scenario2),
                        startedMinions = 5,
                        completedMinions = 3,
                        successfulExecutions = 3,
                        failedExecutions = 2,
                        zones = setOf(),
                        scenariosReports = listOf()
                    )
                )
            )
        }
        coVerifyOrder {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1"))
            campaignConverter.convertToModel(refEq(campaignEntity))
            campaignReportRepository.findByCampaignId(2)
            campaignReportProvider["mapScenarioReport"](listOf<ScenarioReportEntity>())
        }

        confirmVerified(
            campaignRepository,
            campaignConverter,
            campaignScenarioRepository,
            campaignReportRepository,
            scenarioReportMessageRepository
        )
    }

    @Test
    internal fun `should retrieve a complete campaign report in a tenant`() = testDispatcherProvider.runTest {
        // given
        val now = Instant.now()
        val creation = now.minusMillis(123)
        val start = now.minusMillis(12)
        val end = start.plusMillis(790976)
        val scenario1 = mockk<Scenario>()
        val scenario2 = mockk<Scenario>()
        val scenarioReportEntity1 = mockk<ScenarioReportEntity>()
        val scenarioReportEntity2 = mockk<ScenarioReportEntity>()
        val scenarioExecutionDetails1 = mockk<ScenarioExecutionDetails>()
        val scenarioExecutionDetails2 = mockk<ScenarioExecutionDetails>()
        val campaignEntity = mockk<CampaignEntity> {
            every { id } returns 5
        }
        coEvery {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1"))
        } returns listOf(campaignEntity)
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns Campaign(
            version = now,
            key = "key-1",
            creation = creation,
            name = "This is a campaign",
            speedFactor = 123.62,
            scheduledMinions = 123,
            hardTimeout = end.plusSeconds(1),
            softTimeout = end.plusSeconds(5),
            start = start,
            end = end,
            status = ExecutionStatus.SUCCESSFUL,
            failureReason = "The failure",
            configurerName = "my-user",
            aborterName = "my-user",
            scenarios = listOf(scenario1, scenario2),
            zones = setOf("zone-1", "zone-2")
        )
        coEvery {
            campaignReportRepository.findByCampaignId(5)
        } returns CampaignReportEntity(
            id = 1,
            version = now,
            campaignId = 5,
            startedMinions = 5,
            completedMinions = 3,
            successfulExecutions = 3,
            failedExecutions = 2,
            status = ExecutionStatus.FAILED,
            scenariosReports = listOf(scenarioReportEntity1, scenarioReportEntity2)
        )
        coEvery {
            campaignReportProvider["mapScenarioReport"](listOf(scenarioReportEntity1, scenarioReportEntity2))
        } returns listOf(scenarioExecutionDetails1, scenarioExecutionDetails2)

        // when
        val result = campaignReportProvider.retrieveCampaignsReports(tenant = "my-tenant", campaignKeys = listOf("key-1"))

        // then
        assertThat(result).all {
            hasSize(1)
            isEqualTo(
                listOf(
                    CampaignExecutionDetails(
                        version = now,
                        key = "key-1",
                        creation = creation,
                        name = "This is a campaign",
                        speedFactor = 123.62,
                        scheduledMinions = 123,
                        hardTimeout = end.plusSeconds(1),
                        softTimeout = end.plusSeconds(5),
                        start = start,
                        end = end,
                        status = ExecutionStatus.FAILED,
                        failureReason = "The failure",
                        configurerName = "my-user",
                        aborterName = "my-user",
                        zones = setOf("zone-1", "zone-2"),
                        scenarios = listOf(scenario1, scenario2),
                        startedMinions = 5,
                        completedMinions = 3,
                        successfulExecutions = 3,
                        failedExecutions = 2,
                        scenariosReports = listOf(scenarioExecutionDetails1, scenarioExecutionDetails2)
                    )
                )
            )
        }
        coVerifyOrder {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1"))
            campaignConverter.convertToModel(refEq(campaignEntity))
            campaignReportRepository.findByCampaignId(5)
            campaignReportProvider["mapScenarioReport"](listOf(scenarioReportEntity1, scenarioReportEntity2))
        }

        confirmVerified(
            campaignRepository,
            campaignConverter,
            campaignScenarioRepository,
            campaignReportRepository,
            scenarioReportMessageRepository
        )
    }

    @Test
    internal fun `should retrieve a list of campaign reports in a tenant`() = testDispatcherProvider.runTest {
        // given
        val now = Instant.now()
        val creation = now.minusMillis(123)
        val start = now.minusMillis(12)
        val end = start.plusMillis(790976)
        val scenario1 = mockk<Scenario>()
        val scenario2 = mockk<Scenario>()
        val scenarioReportEntity1 = mockk<ScenarioReportEntity>()
        val scenarioReportEntity2 = mockk<ScenarioReportEntity>()
        val scenarioExecutionDetails1 = mockk<ScenarioExecutionDetails>()
        val scenarioExecutionDetails2 = mockk<ScenarioExecutionDetails>()
        val campaignEntity1 = mockk<CampaignEntity> {
            every { id } returns 1
        }
        val campaignEntity2 = mockk<CampaignEntity> {
            every { id } returns 2
        }
        val campaignEntity3 = mockk<CampaignEntity> {
            every { id } returns 3
        }
        val campaignEntity4 = mockk<CampaignEntity> {
            every { id } returns 4
        }
        coEvery {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1", "key-3", "key-2", "key-4"))
        } returns listOf(campaignEntity1, campaignEntity3, campaignEntity2, campaignEntity4)
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity1)) } returns Campaign(
            version = now,
            key = "key-1",
            creation = creation,
            name = "This is the first campaign",
            speedFactor = 1.62,
            scheduledMinions = 1,
            start = start,
            end = end,
            status = ExecutionStatus.SUCCESSFUL,
            configurerName = "my-user",
            scenarios = listOf(scenario1),
            hardTimeout = end.plusSeconds(1),
            softTimeout = end.plusSeconds(5),
            failureReason = "The failure 1",
            aborterName = "my-user",
            zones = setOf("zone-1")
        )
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity3)) } returns Campaign(
            version = now,
            key = "key-3",
            creation = creation,
            name = "This is the third campaign",
            speedFactor = 3.62,
            scheduledMinions = 3,
            start = start,
            end = end,
            status = ExecutionStatus.IN_PROGRESS,
            configurerName = "my-user",
            scenarios = listOf(scenario1, scenario2),
            hardTimeout = end.plusSeconds(3),
            softTimeout = end.plusSeconds(5),
            failureReason = "The failure 3",
            aborterName = "my-user",
            zones = setOf("zone-1", "zone-2")
        )
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity2)) } returns Campaign(
            version = now,
            key = "key-2",
            creation = creation,
            name = "This is the second campaign",
            speedFactor = 2.62,
            scheduledMinions = 2,
            start = start,
            end = end,
            status = ExecutionStatus.SCHEDULED,
            configurerName = "my-user",
            scenarios = listOf(scenario2),
            hardTimeout = end.plusSeconds(2),
            softTimeout = end.plusSeconds(5),
            failureReason = "The failure 2",
            aborterName = "my-user",
            zones = setOf("zone-2")
        )
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity4)) } returns Campaign(
            version = now,
            key = "key-4",
            creation = creation,
            name = "This is the fourth campaign",
            speedFactor = 4.62,
            scheduledMinions = 4,
            start = start,
            end = end,
            status = ExecutionStatus.QUEUED,
            configurerName = "my-user",
            scenarios = listOf(),
            hardTimeout = end.plusSeconds(4),
            softTimeout = end.plusSeconds(5),
            failureReason = "The failure 4",
            aborterName = "my-user",
            zones = setOf()
        )
        coEvery {
            campaignReportRepository.findByCampaignId(1)
        } returns CampaignReportEntity(
            id = 1,
            version = now,
            campaignId = 1,
            startedMinions = 5,
            completedMinions = 3,
            successfulExecutions = 3,
            failedExecutions = 2,
            status = ExecutionStatus.FAILED,
            scenariosReports = listOf(scenarioReportEntity1)
        )
        coEvery {
            campaignReportRepository.findByCampaignId(3)
        } returns CampaignReportEntity(
            id = 3,
            version = now,
            campaignId = 3,
            startedMinions = 5,
            completedMinions = 3,
            successfulExecutions = 3,
            failedExecutions = 2,
            status = ExecutionStatus.IN_PROGRESS,
            scenariosReports = listOf(scenarioReportEntity1, scenarioReportEntity2)
        )
        coEvery {
            campaignReportRepository.findByCampaignId(2)
        } returns CampaignReportEntity(
            id = 2,
            version = now,
            campaignId = 2,
            startedMinions = 5,
            completedMinions = 3,
            successfulExecutions = 3,
            failedExecutions = 2,
            status = ExecutionStatus.SCHEDULED,
            scenariosReports = listOf(scenarioReportEntity2)
        )
        coEvery {
            campaignReportRepository.findByCampaignId(4)
        } returns null
        coEvery {
            campaignReportProvider["mapScenarioReport"](listOf(scenarioReportEntity1, scenarioReportEntity2))
        } returns listOf(scenarioExecutionDetails1, scenarioExecutionDetails2)
        coEvery {
            campaignReportProvider["mapScenarioReport"](listOf(scenarioReportEntity1))
        } returns listOf(scenarioExecutionDetails1)
        coEvery {
            campaignReportProvider["mapScenarioReport"](listOf(scenarioReportEntity2))
        } returns listOf(scenarioExecutionDetails2)
        coEvery {
            campaignReportProvider["ongoingScenariosDetails"](4L)
        } returns listOf<ScenarioExecutionDetails>()

        // when
        val result = campaignReportProvider.retrieveCampaignsReports(
            tenant = "my-tenant",
            campaignKeys = listOf("key-1", "key-3", "key-2", "key-4")
        )

        // then
        assertThat(result).all {
            hasSize(4)
            isEqualTo(
                listOf(
                    CampaignExecutionDetails(
                        version = now,
                        key = "key-1",
                        creation = creation,
                        name = "This is the first campaign",
                        speedFactor = 1.62,
                        scheduledMinions = 1,
                        hardTimeout = end.plusSeconds(1),
                        softTimeout = end.plusSeconds(5),
                        start = start,
                        end = end,
                        status = ExecutionStatus.FAILED,
                        failureReason = "The failure 1",
                        configurerName = "my-user",
                        aborterName = "my-user",
                        zones = setOf("zone-1"),
                        scenarios = listOf(scenario1),
                        startedMinions = 5,
                        completedMinions = 3,
                        successfulExecutions = 3,
                        failedExecutions = 2,
                        scenariosReports = listOf(scenarioExecutionDetails1)
                    ),
                    CampaignExecutionDetails(
                        version = now,
                        key = "key-3",
                        creation = creation,
                        name = "This is the third campaign",
                        speedFactor = 3.62,
                        scheduledMinions = 3,
                        hardTimeout = end.plusSeconds(3),
                        softTimeout = end.plusSeconds(5),
                        start = start,
                        end = end,
                        status = ExecutionStatus.IN_PROGRESS,
                        failureReason = "The failure 3",
                        configurerName = "my-user",
                        aborterName = "my-user",
                        zones = setOf("zone-1", "zone-2"),
                        scenarios = listOf(scenario1, scenario2),
                        startedMinions = 5,
                        completedMinions = 3,
                        successfulExecutions = 3,
                        failedExecutions = 2,
                        scenariosReports = listOf(scenarioExecutionDetails1, scenarioExecutionDetails2)
                    ),
                    CampaignExecutionDetails(
                        version = now,
                        key = "key-2",
                        creation = creation,
                        name = "This is the second campaign",
                        speedFactor = 2.62,
                        scheduledMinions = 2,
                        hardTimeout = end.plusSeconds(2),
                        softTimeout = end.plusSeconds(5),
                        start = start,
                        end = end,
                        status = ExecutionStatus.SCHEDULED,
                        failureReason = "The failure 2",
                        configurerName = "my-user",
                        aborterName = "my-user",
                        zones = setOf("zone-2"),
                        scenarios = listOf(scenario2),
                        startedMinions = 5,
                        completedMinions = 3,
                        successfulExecutions = 3,
                        failedExecutions = 2,
                        scenariosReports = listOf(scenarioExecutionDetails2)
                    ),
                    CampaignExecutionDetails(
                        version = now,
                        key = "key-4",
                        creation = creation,
                        name = "This is the fourth campaign",
                        speedFactor = 4.62,
                        scheduledMinions = 4,
                        hardTimeout = end.plusSeconds(4),
                        softTimeout = end.plusSeconds(5),
                        start = start,
                        end = end,
                        status = ExecutionStatus.QUEUED,
                        failureReason = "The failure 4",
                        configurerName = "my-user",
                        aborterName = "my-user",
                        zones = setOf(),
                        scenarios = listOf(),
                        startedMinions = null,
                        completedMinions = null,
                        successfulExecutions = null,
                        failedExecutions = null,
                        scenariosReports = listOf()
                    )
                )
            )
        }
        coVerifyOrder {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1", "key-3", "key-2", "key-4"))
            campaignConverter.convertToModel(refEq(campaignEntity1))
            campaignReportRepository.findByCampaignId(1)
            campaignReportProvider["mapScenarioReport"](listOf(scenarioReportEntity1))
            campaignConverter.convertToModel(refEq(campaignEntity3))
            campaignReportRepository.findByCampaignId(3)
            campaignReportProvider["mapScenarioReport"](listOf(scenarioReportEntity1, scenarioReportEntity2))
            campaignConverter.convertToModel(refEq(campaignEntity2))
            campaignReportRepository.findByCampaignId(2)
            campaignReportProvider["mapScenarioReport"](listOf(scenarioReportEntity2))
            campaignConverter.convertToModel(refEq(campaignEntity4))
            campaignReportRepository.findByCampaignId(4)
            campaignReportProvider["ongoingScenariosDetails"](4L)
        }

        confirmVerified(
            campaignRepository,
            campaignConverter,
            campaignScenarioRepository,
            campaignReportRepository,
            scenarioReportMessageRepository
        )
    }

    @Test
    internal fun `should return an empty list when keys do not exist`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1"))
        } returns listOf()

        // when
        val result = campaignReportProvider.retrieveCampaignsReports(tenant = "my-tenant", campaignKeys = listOf("key-1"))

        // then
        assertThat(result).isEmpty()
        coVerifyOrder {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1"))
        }

        confirmVerified(
            campaignRepository,
            campaignConverter,
            campaignScenarioRepository,
            campaignReportRepository,
            scenarioReportMessageRepository
        )
    }
}