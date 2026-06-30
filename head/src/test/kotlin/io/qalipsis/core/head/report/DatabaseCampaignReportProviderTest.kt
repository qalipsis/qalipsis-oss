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
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.entity.StepReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportRepository
import io.qalipsis.core.head.jdbc.repository.StepReportRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.Zone
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.zone.ZoneService
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

    @MockK
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @MockK
    private lateinit var stepReportRepository: StepReportRepository

    @MockK
    private lateinit var campaignService: CampaignService

    @MockK
    private lateinit var zoneService: ZoneService

    @MockK
    private lateinit var campaignMeterEnricher: CampaignMeterEnricher

    private lateinit var campaignReportProvider: DatabaseCampaignReportProvider

    private val now: Instant = Instant.parse("2024-01-15T10:00:00Z")
    private val start: Instant = Instant.parse("2024-01-15T09:00:00Z")
    private val end: Instant = Instant.parse("2024-01-15T09:30:00Z")

    @BeforeEach
    internal fun setUp() {
        campaignReportProvider = spyk(
            DatabaseCampaignReportProvider(
                campaignRepository = campaignRepository,
                campaignConverter = campaignConverter,
                campaignScenarioRepository = campaignScenarioRepository,
                campaignReportRepository = campaignReportRepository,
                scenarioReportMessageRepository = scenarioReportMessageRepository,
                scenarioReportRepository = scenarioReportRepository,
                stepReportRepository = stepReportRepository,
                campaignService = campaignService,
                zoneService = zoneService,
                campaignMeterEnricher = campaignMeterEnricher
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
    internal fun `should retrieve campaign reports in bulk using findByCampaignIdIn`() =
        testDispatcherProvider.runTest {
            // given
            val now = Instant.now()
            val creation = now.minusMillis(123)
            val start = now.minusMillis(12)
            val end = start.plusMillis(790976)
            val scenario1 = Scenario(version = now, name = "sc-1", minionsCount = 5)
            val scenarioEntity1 = ScenarioReportEntity(
                id = 10L, version = now, name = "sc-1", campaignReportId = 100L,
                start = start, end = end, startedMinions = 5, completedMinions = 4,
                successfulExecutions = 4, failedExecutions = 1, status = ExecutionStatus.SUCCESSFUL,
                messages = emptyList()
            )
            val campaignEntity1 = mockk<CampaignEntity> { every { id } returns 1L }
            val campaignEntity2 = mockk<CampaignEntity> { every { id } returns 2L }
            coEvery {
                campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1", "key-2"))
            } returns listOf(campaignEntity1, campaignEntity2)
            coEvery { campaignConverter.convertToModel(refEq(campaignEntity1)) } returns Campaign(
                version = now, key = "key-1", creation = creation, name = "Campaign 1",
                speedFactor = 1.0, scheduledMinions = 5, start = start, end = end,
                status = ExecutionStatus.SUCCESSFUL, configurerName = "user-1",
                configuredScenarios = listOf(scenario1), zones = setOf()
            )
            coEvery { campaignConverter.convertToModel(refEq(campaignEntity2)) } returns Campaign(
                version = now, key = "key-2", creation = creation, name = "Campaign 2",
                speedFactor = 1.0, scheduledMinions = 3, start = start, end = null,
                status = ExecutionStatus.IN_PROGRESS, configurerName = "user-1",
                configuredScenarios = listOf(), zones = setOf()
            )
            // key-1 is SUCCESSFUL (completed); key-2 is IN_PROGRESS (running) — reports skipped for key-2.
            coEvery {
                campaignReportRepository.findByCampaignIdIn(listOf(1L))
            } returns listOf(
                CampaignReportEntity(
                    id = 100L, version = now, campaignId = 1L,
                    startedMinions = 5, completedMinions = 4, successfulExecutions = 4, failedExecutions = 1,
                    status = ExecutionStatus.SUCCESSFUL, scenariosReports = listOf(scenarioEntity1)
                )
            )
            // key-2 has no report → falls into ongoing path.
            coEvery {
                campaignScenarioRepository.findByCampaignIdIn(listOf(2L))
            } returns listOf(
                CampaignScenarioEntity(
                    id = 20L,
                    version = now,
                    campaignId = 2L,
                    name = "sc-2",
                    minionsCount = 3,
                    start = start,
                    end = null
                )
            )
            coEvery {
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(10L))
            } returns emptyList()
            coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(10L)) } returns emptyList()
            // Config and meters only fetched for completed campaign key-1.
            coEvery { campaignService.retrieveConfiguration("my-tenant", "key-1") } throws RuntimeException("no config")
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("key-1"), listOf("sc-1"))
            } returns mapOf("key-1" to emptyDistribution())

            // when
            val result = campaignReportProvider.retrieveCampaignsReports("my-tenant", listOf("key-1", "key-2"))

            // then
            assertThat(result).hasSize(2)
            val key1Result = result.first { it.key == "key-1" }
            val key2Result = result.first { it.key == "key-2" }
            assertThat(key1Result.scenarios).hasSize(1)
            assertThat(key1Result.scenarios[0].name).isEqualTo("sc-1")
            assertThat(key2Result.scenarios).hasSize(1)
            assertThat(key2Result.scenarios[0].name).isEqualTo("sc-2")
            // Report repo called only for completed campaign; scenario repo for the running one.
            coVerify { campaignReportRepository.findByCampaignIdIn(listOf(1L)) }
            coVerify { campaignScenarioRepository.findByCampaignIdIn(listOf(2L)) }
            coVerify { campaignMeterEnricher.distribute("my-tenant", listOf("key-1"), listOf("sc-1")) }
        }

    @Test
    internal fun `should not call report repositories or meter enricher for running campaigns`() =
        testDispatcherProvider.runTest {
            // given
            val now = Instant.now()
            val start = now.minusSeconds(60)
            val campaignEntity1 = mockk<CampaignEntity> { every { id } returns 1L }
            val campaignEntity2 = mockk<CampaignEntity> { every { id } returns 2L }
            coEvery {
                campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1", "key-2"))
            } returns listOf(campaignEntity1, campaignEntity2)
            coEvery { campaignConverter.convertToModel(refEq(campaignEntity1)) } returns Campaign(
                version = now, key = "key-1", creation = now.minusSeconds(3600), name = "Campaign 1",
                speedFactor = 1.0, scheduledMinions = 5, start = start, end = null,
                status = ExecutionStatus.IN_PROGRESS, configurerName = "user-1",
                configuredScenarios = listOf(), zones = setOf()
            )
            coEvery { campaignConverter.convertToModel(refEq(campaignEntity2)) } returns Campaign(
                version = now, key = "key-2", creation = now.minusSeconds(3600), name = "Campaign 2",
                speedFactor = 1.0, scheduledMinions = 3, start = null, end = null,
                status = ExecutionStatus.QUEUED, configurerName = "user-1",
                configuredScenarios = listOf(), zones = setOf()
            )
            // Both campaigns are running → scenarios via campaignScenarioRepository.
            coEvery {
                campaignScenarioRepository.findByCampaignIdIn(listOf(1L, 2L))
            } returns listOf(
                CampaignScenarioEntity(
                    id = 10L,
                    version = now,
                    campaignId = 1L,
                    name = "sc-1",
                    minionsCount = 5,
                    start = start,
                    end = null
                ),
                CampaignScenarioEntity(
                    id = 20L,
                    version = now,
                    campaignId = 2L,
                    name = "sc-2",
                    minionsCount = 3,
                    start = null,
                    end = null
                )
            )
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()

            // when
            val result = campaignReportProvider.retrieveCampaignsReports("my-tenant", listOf("key-1", "key-2"))

            // then
            assertThat(result).hasSize(2)
            val key1Result = result.first { it.key == "key-1" }
            val key2Result = result.first { it.key == "key-2" }
            assertThat(key1Result.scenarios).hasSize(1)
            assertThat(key1Result.scenarios[0].name).isEqualTo("sc-1")
            assertThat(key1Result.scenarios[0].status).isEqualTo(ExecutionStatus.IN_PROGRESS)
            assertThat(key2Result.scenarios).hasSize(1)
            assertThat(key2Result.scenarios[0].name).isEqualTo("sc-2")
            assertThat(key2Result.scenarios[0].status).isEqualTo(ExecutionStatus.QUEUED)

            coVerify { campaignScenarioRepository.findByCampaignIdIn(listOf(1L, 2L)) }
            // Report repositories, config, and meters must NOT be called for running campaigns.
            coVerify(exactly = 0) { campaignReportRepository.findByCampaignIdIn(any()) }
            coVerify(exactly = 0) { scenarioReportRepository.findByCampaignReportIdIn(any()) }
            coVerify(exactly = 0) { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(any()) }
            coVerify(exactly = 0) { stepReportRepository.findByScenarioReportIdIn(any()) }
            coVerify(exactly = 0) { campaignService.retrieveConfiguration(any(), any()) }
            coVerify(exactly = 0) { campaignMeterEnricher.distribute(any(), any(), any()) }
            confirmVerified(
                campaignReportRepository,
                scenarioReportRepository,
                scenarioReportMessageRepository,
                stepReportRepository,
                campaignService,
                campaignMeterEnricher
            )
        }

    @Test
    internal fun `should fetch scenario messages and steps in bulk across all campaigns`() =
        testDispatcherProvider.runTest {
            // given
            val scenario1 = ScenarioReportEntity(
                id = 10L, version = now, name = "sc-1", campaignReportId = 100L,
                start = start, end = end, startedMinions = 5, completedMinions = 4,
                successfulExecutions = 4, failedExecutions = 0, status = ExecutionStatus.SUCCESSFUL,
                messages = emptyList()
            )
            val scenario2 = ScenarioReportEntity(
                id = 20L, version = now, name = "sc-2", campaignReportId = 200L,
                start = start, end = end, startedMinions = 3, completedMinions = 3,
                successfulExecutions = 3, failedExecutions = 0, status = ExecutionStatus.SUCCESSFUL,
                messages = emptyList()
            )
            val campaignEntity1 = mockk<CampaignEntity> { every { id } returns 1L }
            val campaignEntity2 = mockk<CampaignEntity> { every { id } returns 2L }
            coEvery {
                campaignRepository.findByTenantAndKeys("my-tenant", listOf("key-1", "key-2"))
            } returns listOf(campaignEntity1, campaignEntity2)
            coEvery { campaignConverter.convertToModel(refEq(campaignEntity1)) } returns buildCampaign("key-1")
            coEvery { campaignConverter.convertToModel(refEq(campaignEntity2)) } returns buildCampaign("key-2")
            coEvery {
                campaignReportRepository.findByCampaignIdIn(listOf(1L, 2L))
            } returns listOf(
                CampaignReportEntity(
                    id = 100L, version = now, campaignId = 1L,
                    startedMinions = 5, completedMinions = 4, successfulExecutions = 4, failedExecutions = 0,
                    status = ExecutionStatus.SUCCESSFUL, scenariosReports = listOf(scenario1)
                ),
                CampaignReportEntity(
                    id = 200L, version = now, campaignId = 2L,
                    startedMinions = 3, completedMinions = 3, successfulExecutions = 3, failedExecutions = 0,
                    status = ExecutionStatus.SUCCESSFUL, scenariosReports = listOf(scenario2)
                )
            )
            // Both scenario IDs must be fetched in a single bulk call.
        coEvery {
            scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(10L, 20L))
        } returns emptyList()
            coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(10L, 20L)) } returns emptyList()
            coEvery { campaignService.retrieveConfiguration("my-tenant", "key-1") } throws RuntimeException("no config")
            coEvery { campaignService.retrieveConfiguration("my-tenant", "key-2") } throws RuntimeException("no config")
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("key-1", "key-2"), listOf("sc-1", "sc-2"))
            } returns mapOf("key-1" to emptyDistribution(), "key-2" to emptyDistribution())

            // when
            val result = campaignReportProvider.retrieveCampaignsReports("my-tenant", listOf("key-1", "key-2"))

            // then
            assertThat(result).hasSize(2)
            // Verify messages and steps were fetched in a single bulk call with all scenario IDs.
            coVerify { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(10L, 20L)) }
            coVerify { stepReportRepository.findByScenarioReportIdIn(listOf(10L, 20L)) }
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

    // -------------------------------------------------------------------------
    // retrieve() — tests migrated from DatabaseCampaignExecutionDetailsServiceTest
    // -------------------------------------------------------------------------

    private fun buildCampaign(key: String = "camp-1"): Campaign = Campaign(
        version = now,
        key = key,
        creation = now.minusSeconds(3600),
        name = "My Campaign",
        speedFactor = 1.0,
        scheduledMinions = 10,
        start = start,
        end = end,
        status = ExecutionStatus.SUCCESSFUL,
        configurerName = "user-1",
        configuredScenarios = emptyList(),
        zones = setOf("fr", "de")
    )

    private fun buildScenarioEntity(
        id: Long = 1L,
        name: String = "scenario-1",
        reportId: Long = 100L
    ): ScenarioReportEntity = ScenarioReportEntity(
        id = id,
        version = now,
        name = name,
        campaignReportId = reportId,
        start = start,
        end = end,
        startedMinions = 5,
        completedMinions = 4,
        successfulExecutions = 4,
        failedExecutions = 1,
        status = ExecutionStatus.SUCCESSFUL,
        messages = emptyList()
    )

    private fun emptyDistribution(): MeterDistribution = MeterDistribution(
        campaignMeters = emptyList(),
        byScenario = emptyMap(),
        byScenarioAndStep = emptyMap()
    )

    @Test
    internal fun `should throw an error when campaign is not found`() = testDispatcherProvider.runTest {
        // given
        coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns emptyList()

        // when / then
        val exception = runCatching { campaignReportProvider.retrieve("my-tenant", "camp-1") }.exceptionOrNull()
        assertThat(exception).isNotNull().isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).isNotNull().messageContains("camp-1")

        coVerify { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) }
        confirmVerified(
            campaignRepository,
            campaignReportRepository,
            scenarioReportRepository,
            scenarioReportMessageRepository,
            stepReportRepository,
            campaignConverter,
            campaignService,
            zoneService,
            campaignMeterEnricher,
            campaignScenarioRepository
        )
    }

    @Test
    internal fun `should return empty scenario reports when no campaign report exists`() =
        testDispatcherProvider.runTest {
            // given
            val campaignEntity = mockk<CampaignEntity> { every { id } returns 42L }
            val campaign = buildCampaign()
            coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(
                campaignEntity
            )
            coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
            coEvery { campaignReportRepository.findByCampaignIdIn(listOf(42L)) } returns emptyList()
            coEvery { campaignScenarioRepository.findByCampaignIdIn(listOf(42L)) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), emptyList())
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignService.retrieveConfiguration(
                    "my-tenant",
                    "camp-1"
                )
            } throws RuntimeException("no config")

            // when
            val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios).isEqualTo(emptyList())
            assertThat(result.key).isEqualTo("camp-1")
            assertThat(result.name).isEqualTo("My Campaign")
            assertThat(result.status).isEqualTo(ExecutionStatus.SUCCESSFUL)

            coVerify {
                campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
                campaignConverter.convertToModel(campaignEntity)
                campaignReportRepository.findByCampaignIdIn(listOf(42L))
                campaignScenarioRepository.findByCampaignIdIn(listOf(42L))
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), emptyList())
                zoneService.resolve("my-tenant", emptySet())
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            }
            confirmVerified(
                campaignRepository,
                campaignReportRepository,
                scenarioReportRepository,
                scenarioReportMessageRepository,
                stepReportRepository,
                campaignConverter,
                campaignService,
                zoneService,
                campaignMeterEnricher,
                campaignScenarioRepository
            )
        }

    @Test
    internal fun `should use inline scenario reports from campaign report when available`() =
        testDispatcherProvider.runTest {
            // given
            val campaignEntity = mockk<CampaignEntity> { every { id } returns 10L }
            val campaign = buildCampaign()
            val scenarioEntity = buildScenarioEntity(id = 1L, name = "sc-1", reportId = 99L)
            val campaignReportEntity = CampaignReportEntity(
                id = 99L,
                version = now,
                campaignId = 10L,
                startedMinions = 5,
                completedMinions = 4,
                successfulExecutions = 4,
                failedExecutions = 0,
                status = ExecutionStatus.SUCCESSFUL,
                scenariosReports = listOf(scenarioEntity)
            )
            coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(
                campaignEntity
            )
            coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
            coEvery { campaignReportRepository.findByCampaignIdIn(listOf(10L)) } returns listOf(campaignReportEntity)
            coEvery {
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(1L))
            } returns emptyList()
            coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(1L)) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            } throws RuntimeException("no config")

            // when
            val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios.size).isEqualTo(1)
            assertThat(result.scenarios[0].name).isEqualTo("sc-1")
            coVerify {
                campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
                campaignConverter.convertToModel(campaignEntity)
                campaignReportRepository.findByCampaignIdIn(listOf(10L))
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(1L))
                stepReportRepository.findByScenarioReportIdIn(listOf(1L))
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
                zoneService.resolve("my-tenant", emptySet())
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            }
            // scenarioReportRepository should NOT be called because inline reports were non-empty
            coVerify(exactly = 0) { scenarioReportRepository.findByCampaignReportIdIn(any()) }
            confirmVerified(
                campaignRepository,
                campaignReportRepository,
                scenarioReportRepository,
                scenarioReportMessageRepository,
                stepReportRepository,
                campaignConverter,
                campaignService,
                zoneService,
                campaignMeterEnricher,
                campaignScenarioRepository
            )
        }

    @Test
    internal fun `should fall back to scenario report repository when inline scenario reports are empty`() =
        testDispatcherProvider.runTest {
            // given
            val campaignEntity = mockk<CampaignEntity> { every { id } returns 20L }
            val campaign = buildCampaign()
            val campaignReportEntity = CampaignReportEntity(
                id = 200L,
                version = now,
                campaignId = 20L,
                startedMinions = 3,
                completedMinions = 2,
                successfulExecutions = 2,
                failedExecutions = 1,
                status = ExecutionStatus.WARNING,
                scenariosReports = emptyList()
            )
            val scenarioEntity = buildScenarioEntity(id = 50L, name = "sc-fallback", reportId = 200L)
            coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(
                campaignEntity
            )
            coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
            coEvery { campaignReportRepository.findByCampaignIdIn(listOf(20L)) } returns listOf(campaignReportEntity)
            coEvery { scenarioReportRepository.findByCampaignReportIdIn(listOf(200L)) } returns listOf(scenarioEntity)
            coEvery {
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(50L))
            } returns emptyList()
            coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(50L)) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-fallback"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            } throws RuntimeException("no config")

            // when
            val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios.size).isEqualTo(1)
            assertThat(result.scenarios[0].name).isEqualTo("sc-fallback")
            coVerify {
                campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
                campaignConverter.convertToModel(campaignEntity)
                campaignReportRepository.findByCampaignIdIn(listOf(20L))
                scenarioReportRepository.findByCampaignReportIdIn(listOf(200L))
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(50L))
                stepReportRepository.findByScenarioReportIdIn(listOf(50L))
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-fallback"))
                zoneService.resolve("my-tenant", emptySet())
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            }
            confirmVerified(
                campaignRepository,
                campaignReportRepository,
                scenarioReportRepository,
                scenarioReportMessageRepository,
                stepReportRepository,
                campaignConverter,
                campaignService,
                zoneService,
                campaignMeterEnricher,
                campaignScenarioRepository
            )
        }

    private fun setupForStepStatusTest(
        step: StepReportEntity,
        messages: List<ScenarioReportMessageEntity> = emptyList()
    ): suspend () -> CampaignExecutionDetails {
        val campaignEntity = mockk<CampaignEntity> { every { id } returns 1L }
        val campaign = buildCampaign()
        val scenarioEntity = buildScenarioEntity(id = 10L, name = "sc-1", reportId = 100L)
        val campaignReportEntity = CampaignReportEntity(
            id = 100L,
            version = now,
            campaignId = 1L,
            startedMinions = 5,
            completedMinions = 5,
            successfulExecutions = 5,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(scenarioEntity)
        )
        coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(campaignEntity)
        coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
        coEvery { campaignReportRepository.findByCampaignIdIn(listOf(1L)) } returns listOf(campaignReportEntity)
        coEvery {
            scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(10L))
        } returns messages
        coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(10L)) } returns listOf(step)
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to emptyDistribution())
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery {
            campaignService.retrieveConfiguration("my-tenant", "camp-1")
        } throws RuntimeException("no config")
        return { campaignReportProvider.retrieve("my-tenant", "camp-1") }
    }

    @Test
    internal fun `step status should be FAILED when step is not initialized`() = testDispatcherProvider.runTest {
        // given
        val step = StepReportEntity(
            scenarioReportId = 10L, name = "step-1", dagId = "dag-1",
            isUnderLoad = true, initialized = false
        )
        val retrieve = setupForStepStatusTest(step)

        // when
        val result = retrieve()

        // then
        val stepDetails = result.scenarios[0].steps[0]
        assertThat(stepDetails.status).isEqualTo(ExecutionStatus.FAILED)
    }

    @Test
    internal fun `step status should be FAILED when step has initialization error`() = testDispatcherProvider.runTest {
        // given
        val step = StepReportEntity(
            scenarioReportId = 10L, name = "step-1", dagId = "dag-1",
            isUnderLoad = true, initialized = true, initializationError = "Connection refused"
        )
        val retrieve = setupForStepStatusTest(step)

        // when
        val result = retrieve()

        // then
        val stepDetails = result.scenarios[0].steps[0]
        assertThat(stepDetails.status).isEqualTo(ExecutionStatus.FAILED)
    }

    @Test
    internal fun `step status should be FAILED when there are ERROR messages for the step`() =
        testDispatcherProvider.runTest {
            // given
            val step = StepReportEntity(
                scenarioReportId = 10L, name = "step-1", dagId = "dag-1",
                isUnderLoad = true, initialized = true
            )
            val messages = listOf(
                ScenarioReportMessageEntity(10L, "step-1", "msg-1", ReportMessageSeverity.ERROR, "Something failed")
            )
            val retrieve = setupForStepStatusTest(step, messages)

            // when
            val result = retrieve()

            // then
            val stepDetails = result.scenarios[0].steps[0]
            assertThat(stepDetails.status).isEqualTo(ExecutionStatus.FAILED)
        }

    @Test
    internal fun `step status should be WARNING when there are WARN messages for the step`() =
        testDispatcherProvider.runTest {
            // given
            val step = StepReportEntity(
                scenarioReportId = 10L, name = "step-1", dagId = "dag-1",
                isUnderLoad = true, initialized = true
            )
            val messages = listOf(
                ScenarioReportMessageEntity(10L, "step-1", "msg-1", ReportMessageSeverity.WARN, "Degraded")
            )
            val retrieve = setupForStepStatusTest(step, messages)

            // when
            val result = retrieve()

            // then
            val stepDetails = result.scenarios[0].steps[0]
            assertThat(stepDetails.status).isEqualTo(ExecutionStatus.WARNING)
        }

    @Test
    internal fun `step status should be WARNING when failedExecutions is greater than zero`() =
        testDispatcherProvider.runTest {
            // given
            val step = StepReportEntity(
                scenarioReportId = 10L, name = "step-1", dagId = "dag-1",
                isUnderLoad = true, initialized = true,
                successfulExecutions = 8L, failedExecutions = 2L
            )
            val retrieve = setupForStepStatusTest(step)

            // when
            val result = retrieve()

            // then
            val stepDetails = result.scenarios[0].steps[0]
            assertThat(stepDetails.status).isEqualTo(ExecutionStatus.WARNING)
        }

    @Test
    internal fun `step status should be SUCCESSFUL when all checks pass`() = testDispatcherProvider.runTest {
        // given
        val step = StepReportEntity(
            scenarioReportId = 10L, name = "step-1", dagId = "dag-1",
            isUnderLoad = true, initialized = true,
            successfulExecutions = 10L, failedExecutions = 0L
        )
        val retrieve = setupForStepStatusTest(step)

        // when
        val result = retrieve()

        // then
        val stepDetails = result.scenarios[0].steps[0]
        assertThat(stepDetails.status).isEqualTo(ExecutionStatus.SUCCESSFUL)
    }

    @Test
    internal fun `should populate zone distribution from campaign configuration per scenario`() =
        testDispatcherProvider.runTest {
            // given
            val campaignEntity = mockk<CampaignEntity> { every { id } returns 5L }
            val campaign = buildCampaign()
            val scenarioEntity = buildScenarioEntity(id = 11L, name = "sc-1", reportId = 500L)
            val campaignReportEntity = CampaignReportEntity(
                id = 500L,
                version = now,
                campaignId = 5L,
                startedMinions = 2,
                completedMinions = 2,
                successfulExecutions = 2,
                failedExecutions = 0,
                status = ExecutionStatus.SUCCESSFUL,
                scenariosReports = listOf(scenarioEntity)
            )
            val campaignConfig = CampaignConfiguration(
                name = "My Campaign",
                scenarios = mapOf(
                    "sc-1" to ScenarioRequest(minionsCount = 10, zones = mapOf("fr" to 60, "de" to 40))
                )
            )
            coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(
                campaignEntity
            )
            coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
            coEvery { campaignReportRepository.findByCampaignIdIn(listOf(5L)) } returns listOf(campaignReportEntity)
            coEvery {
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(11L))
            } returns emptyList()
            coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(11L)) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { zoneService.resolve("my-tenant", setOf("fr", "de")) } returns emptyList()
            coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } returns campaignConfig

            // when
            val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

            // then
            val scenarioReport = result.scenarios[0]
            assertThat(scenarioReport.zoneDistribution).isEqualTo(mapOf("fr" to 60, "de" to 40))
            coVerify {
                campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
                campaignConverter.convertToModel(campaignEntity)
                campaignReportRepository.findByCampaignIdIn(listOf(5L))
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(11L))
                stepReportRepository.findByScenarioReportIdIn(listOf(11L))
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
                zoneService.resolve("my-tenant", setOf("fr", "de"))
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            }
            confirmVerified(
                campaignRepository,
                campaignReportRepository,
                scenarioReportRepository,
                scenarioReportMessageRepository,
                stepReportRepository,
                campaignConverter,
                campaignService,
                zoneService,
                campaignMeterEnricher,
                campaignScenarioRepository
            )
        }

    @Test
    internal fun `should default zone distribution to empty map when no campaign configuration exists`() =
        testDispatcherProvider.runTest {
            // given
            val campaignEntity = mockk<CampaignEntity> { every { id } returns 6L }
            val campaign = buildCampaign()
            val scenarioEntity = buildScenarioEntity(id = 12L, name = "sc-1", reportId = 600L)
            val campaignReportEntity = CampaignReportEntity(
                id = 600L,
                version = now,
                campaignId = 6L,
                startedMinions = 1,
                completedMinions = 1,
                successfulExecutions = 1,
                failedExecutions = 0,
                status = ExecutionStatus.SUCCESSFUL,
                scenariosReports = listOf(scenarioEntity)
            )
            coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(
                campaignEntity
            )
            coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
            coEvery { campaignReportRepository.findByCampaignIdIn(listOf(6L)) } returns listOf(campaignReportEntity)
            coEvery {
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(12L))
            } returns emptyList()
            coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(12L)) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            } throws RuntimeException("config not found")

            // when
            val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

            // then
            val scenarioReport = result.scenarios[0]
            assertThat(scenarioReport.zoneDistribution).isEqualTo(emptyMap())
            coVerify {
                campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
                campaignConverter.convertToModel(campaignEntity)
                campaignReportRepository.findByCampaignIdIn(listOf(6L))
                scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(12L))
                stepReportRepository.findByScenarioReportIdIn(listOf(12L))
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
                zoneService.resolve("my-tenant", emptySet())
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            }
            confirmVerified(
                campaignRepository,
                campaignReportRepository,
                scenarioReportRepository,
                scenarioReportMessageRepository,
                stepReportRepository,
                campaignConverter,
                campaignService,
                zoneService,
                campaignMeterEnricher,
                campaignScenarioRepository
            )
        }

    @Test
    internal fun `should populate resolvedZones from zone service response`() = testDispatcherProvider.runTest {
        // given
        val campaignEntity = mockk<CampaignEntity> { every { id } returns 8L }
        val campaign = buildCampaign()
        val scenarioEntity = buildScenarioEntity(id = 13L, name = "sc-1", reportId = 700L)
        val campaignReportEntity = CampaignReportEntity(
            id = 700L, version = now, campaignId = 8L,
            startedMinions = 2, completedMinions = 2, successfulExecutions = 2, failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL, scenariosReports = listOf(scenarioEntity)
        )
        val campaignConfig = CampaignConfiguration(
            name = "My Campaign",
            scenarios = mapOf("sc-1" to ScenarioRequest(minionsCount = 10, zones = mapOf("fr" to 60, "de" to 40)))
        )
        val zoneFr = Zone(key = "fr", title = "France")
        val zoneDe = Zone(key = "de", title = "Germany")
        coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(campaignEntity)
        coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
        coEvery { campaignReportRepository.findByCampaignIdIn(listOf(8L)) } returns listOf(campaignReportEntity)
        coEvery { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(13L)) } returns emptyList()
        coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(13L)) } returns emptyList()
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to emptyDistribution())
        coEvery { zoneService.resolve("my-tenant", setOf("fr", "de")) } returns listOf(zoneFr, zoneDe)
        coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } returns campaignConfig

        // when
        val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

        // then
        assertThat(result.resolvedZones.map { it.key }.toSet()).isEqualTo(setOf("fr", "de"))
        coVerify {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
            campaignConverter.convertToModel(campaignEntity)
            campaignReportRepository.findByCampaignIdIn(listOf(8L))
            scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(13L))
            stepReportRepository.findByScenarioReportIdIn(listOf(13L))
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            zoneService.resolve("my-tenant", setOf("fr", "de"))
            campaignService.retrieveConfiguration("my-tenant", "camp-1")
        }
        confirmVerified(
            campaignRepository, campaignReportRepository, scenarioReportRepository,
            scenarioReportMessageRepository, stepReportRepository, campaignConverter,
            campaignService, zoneService, campaignMeterEnricher, campaignScenarioRepository
        )
    }

    @Test
    internal fun `should populate scenario level meters from meter distribution`() = testDispatcherProvider.runTest {
        // given
        val campaignEntity = mockk<CampaignEntity> { every { id } returns 9L }
        val campaign = buildCampaign()
        val scenarioEntity = buildScenarioEntity(id = 14L, name = "sc-1", reportId = 800L)
        val campaignReportEntity = CampaignReportEntity(
            id = 800L, version = now, campaignId = 9L,
            startedMinions = 5, completedMinions = 5, successfulExecutions = 5, failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL, scenariosReports = listOf(scenarioEntity)
        )
        val scenarioMeter = TimeSeriesMeter(name = "rps", timestamp = now, type = "gauge", campaign = "camp-1")
        val distribution = MeterDistribution(
            campaignMeters = emptyList(),
            byScenario = mapOf("sc-1" to listOf(scenarioMeter)),
            byScenarioAndStep = emptyMap()
        )
        coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(campaignEntity)
        coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
        coEvery { campaignReportRepository.findByCampaignIdIn(listOf(9L)) } returns listOf(campaignReportEntity)
        coEvery { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(14L)) } returns emptyList()
        coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(14L)) } returns emptyList()
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to distribution)
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } throws RuntimeException("no config")

        // when
        val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

        // then
        assertThat(result.scenarios[0].meters).isEqualTo(listOf(scenarioMeter))
        coVerify {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
            campaignConverter.convertToModel(campaignEntity)
            campaignReportRepository.findByCampaignIdIn(listOf(9L))
            scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(14L))
            stepReportRepository.findByScenarioReportIdIn(listOf(14L))
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            zoneService.resolve("my-tenant", emptySet())
            campaignService.retrieveConfiguration("my-tenant", "camp-1")
        }
        confirmVerified(
            campaignRepository, campaignReportRepository, scenarioReportRepository,
            scenarioReportMessageRepository, stepReportRepository, campaignConverter,
            campaignService, zoneService, campaignMeterEnricher, campaignScenarioRepository
        )
    }

    @Test
    internal fun `should populate step level meters from meter distribution`() = testDispatcherProvider.runTest {
        // given
        val campaignEntity = mockk<CampaignEntity> { every { id } returns 10L }
        val campaign = buildCampaign()
        val scenarioEntity = buildScenarioEntity(id = 15L, name = "sc-1", reportId = 900L)
        val campaignReportEntity = CampaignReportEntity(
            id = 900L, version = now, campaignId = 10L,
            startedMinions = 5, completedMinions = 5, successfulExecutions = 5, failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL, scenariosReports = listOf(scenarioEntity)
        )
        val stepEntity = StepReportEntity(
            scenarioReportId = 15L, name = "step-1", dagId = "dag-1", isUnderLoad = true, initialized = true
        )
        val stepMeter = TimeSeriesMeter(name = "latency", timestamp = now, type = "timer", campaign = "camp-1")
        val distribution = MeterDistribution(
            campaignMeters = emptyList(),
            byScenario = emptyMap(),
            byScenarioAndStep = mapOf("sc-1" to mapOf("step-1" to listOf(stepMeter)))
        )
        coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(campaignEntity)
        coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
        coEvery { campaignReportRepository.findByCampaignIdIn(listOf(10L)) } returns listOf(campaignReportEntity)
        coEvery { scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(15L)) } returns emptyList()
        coEvery { stepReportRepository.findByScenarioReportIdIn(listOf(15L)) } returns listOf(stepEntity)
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to distribution)
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } throws RuntimeException("no config")

        // when
        val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

        // then
        assertThat(result.scenarios[0].steps[0].meters).isEqualTo(listOf(stepMeter))
        coVerify {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
            campaignConverter.convertToModel(campaignEntity)
            campaignReportRepository.findByCampaignIdIn(listOf(10L))
            scenarioReportMessageRepository.findByScenarioReportIdInOrderById(listOf(15L))
            stepReportRepository.findByScenarioReportIdIn(listOf(15L))
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            zoneService.resolve("my-tenant", emptySet())
            campaignService.retrieveConfiguration("my-tenant", "camp-1")
        }
        confirmVerified(
            campaignRepository, campaignReportRepository, scenarioReportRepository,
            scenarioReportMessageRepository, stepReportRepository, campaignConverter,
            campaignService, zoneService, campaignMeterEnricher, campaignScenarioRepository
        )
    }

    @Test
    internal fun `should set campaign level meters from meter distribution`() = testDispatcherProvider.runTest {
        // given
        val campaignEntity = mockk<CampaignEntity> { every { id } returns 7L }
        val campaign = buildCampaign()
        val campaignMeter = TimeSeriesMeter(
            name = "cpu", timestamp = now, type = "gauge", campaign = "camp-1"
        )
        val distribution = MeterDistribution(
            campaignMeters = listOf(campaignMeter),
            byScenario = emptyMap(),
            byScenarioAndStep = emptyMap()
        )
        coEvery { campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1")) } returns listOf(campaignEntity)
        coEvery { campaignConverter.convertToModel(campaignEntity) } returns campaign
        coEvery { campaignReportRepository.findByCampaignIdIn(listOf(7L)) } returns emptyList()
        coEvery { campaignScenarioRepository.findByCampaignIdIn(listOf(7L)) } returns emptyList()
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), emptyList())
        } returns mapOf("camp-1" to distribution)
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } throws RuntimeException("no config")

        // when
        val result = campaignReportProvider.retrieve("my-tenant", "camp-1")

        // then
        assertThat(result.meters).isEqualTo(listOf(campaignMeter))
        coVerify {
            campaignRepository.findByTenantAndKeys("my-tenant", listOf("camp-1"))
            campaignConverter.convertToModel(campaignEntity)
            campaignReportRepository.findByCampaignIdIn(listOf(7L))
            campaignScenarioRepository.findByCampaignIdIn(listOf(7L))
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), emptyList())
            zoneService.resolve("my-tenant", emptySet())
            campaignService.retrieveConfiguration("my-tenant", "camp-1")
        }
        confirmVerified(
            campaignRepository,
            campaignReportRepository,
            scenarioReportRepository,
            scenarioReportMessageRepository,
            stepReportRepository,
            campaignConverter,
            campaignService,
            zoneService,
            campaignMeterEnricher,
            campaignScenarioRepository
        )
    }
}