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
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.mockk.coEvery
import io.mockk.coExcludeRecords
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportFileEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository.CampaignKeyAndName
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.jdbc.repository.ReportDataComponentRepository
import io.qalipsis.core.head.jdbc.repository.ReportFileRepository
import io.qalipsis.core.head.jdbc.repository.ReportRepository
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataTable
import io.qalipsis.core.head.model.DataTableCreationAndUpdateRequest
import io.qalipsis.core.head.model.Diagram
import io.qalipsis.core.head.model.DiagramCreationAndUpdateRequest
import io.qalipsis.core.head.model.Report
import io.qalipsis.core.head.model.ReportCreationAndUpdateRequest
import io.qalipsis.core.head.model.ReportTaskStatus
import io.qalipsis.core.head.model.converter.ReportConverter
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_CAMPAIGN_KEYS_NOT_ALLOWED
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_DATA_SERIES_NOT_ALLOWED
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_DELETE_DENY
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_FETCH_DENY
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_UPDATE_DENY
import io.qalipsis.core.lifetime.ExitStatusException
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.time.Instant

/**
 * @author Joël Valère
 */

@WithMockk
internal class ReportServiceImplTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var reportRepository: ReportRepository

    @MockK
    private lateinit var tenantRepository: TenantRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    @MockK
    private lateinit var dataSeriesRepository: DataSeriesRepository

    @MockK
    private lateinit var reportDataComponentRepository: ReportDataComponentRepository

    @MockK
    private lateinit var reportTaskRepository: ReportTaskRepository

    @MockK
    private lateinit var reportFileRepository: ReportFileRepository

    @MockK
    private lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    private lateinit var reportConverter: ReportConverter

    @MockK
    private lateinit var reportGenerator: ReportGenerator

    private val dataSeries = listOf(
        DataSeriesEntity(
            reference = "series-ref-1",
            tenantId = 123L,
            creatorId = 456L,
            displayName = "series-name-1",
            dataType = DataType.EVENTS,
            valueName = "my-event"
        ),
        DataSeriesEntity(
            reference = "series-ref-2",
            tenantId = 123L,
            creatorId = 456L,
            displayName = "series-name-2",
            dataType = DataType.METERS,
            valueName = "my-meter"
        )
    )
    private val reportEntity = ReportEntity(
        tenantId = 42L,
        displayName = "current-report",
        reference = "qoi78wizened",
        creatorId = 4L,
        campaignKeys = listOf("key1", "key2"),
        campaignNamesPatterns = emptyList(),
        scenarioNamesPatterns = emptyList(),
        dataComponents = listOf(
            ReportDataComponentEntity(
                id = 1, type = DataComponentType.DIAGRAM, -1, listOf(
                    DataSeriesEntity(
                        reference = "data-series-1",
                        tenantId = -1,
                        creatorId = -1,
                        displayName = "data-series-1",
                        dataType = DataType.METERS,
                        valueName = "my-value",
                        color = "#FF0000",
                        filters = setOf(DataSeriesFilterEntity("minionsCount", QueryClauseOperator.IS, "1000")),
                        timeframeUnitMs = 10_000L,
                        fieldName = "my-field",
                        aggregationOperation = QueryAggregationOperator.AVERAGE,
                        displayFormat = "#000.000",
                        query = "This is the query",
                        colorOpacity = null
                    )
                )
            ),
            ReportDataComponentEntity(
                id = 2, type = DataComponentType.DATA_TABLE, -1, listOf(
                    DataSeriesEntity(
                        reference = "data-series-2",
                        tenantId = -1,
                        creatorId = -1,
                        displayName = "data-series-2",
                        dataType = DataType.EVENTS,
                        valueName = "my-value2",
                        color = "#FF0000",
                        filters = setOf(DataSeriesFilterEntity("minionsCount", QueryClauseOperator.IS, "1000")),
                        timeframeUnitMs = 10_000L,
                        fieldName = "my-field",
                        aggregationOperation = QueryAggregationOperator.AVERAGE,
                        displayFormat = "#000.000",
                        query = "This is the query",
                        colorOpacity = null
                    )
                )
            )
        )
    )
    private val now: Instant = Instant.parse("2022-02-22T00:00:00.00Z")
    private val reportEntity2: ReportEntity = reportEntity.copy(dataComponents = emptyList(), id = 7)
    private val reportTaskEntity = ReportTaskEntity(
        creator = "user",
        creationTimestamp = now,
        id = 11,
        reportId = reportEntity2.id,
        reference = "report-task-1",
        tenantReference = "my-tenant",
        status = ReportTaskStatus.PENDING,
        updateTimestamp = now
    )

    @Test
    internal fun `should create the report with the default sharing mode and empty campaign keys and scenario names patterns`() =
        testDispatcherProvider.runTest {
            // given
            val reportEntity = ReportEntity(
                reference = "the-reference",
                tenantId = 123L,
                creatorId = 456L,
                displayName = "report-name",
                sharingMode = SharingMode.READONLY,
                campaignKeys = listOf(),
                campaignNamesPatterns = listOf(),
                scenarioNamesPatterns = listOf(),
                dataComponents = listOf()
            )
            val report = Report(
                reference = "the-reference",
                version = Instant.EPOCH,
                creator = "the-user",
                displayName = "report-name",
                sharingMode = SharingMode.READONLY,
                campaignKeys = listOf(),
                campaignNamesPatterns = listOf(),
                resolvedCampaigns = listOf(),
                scenarioNamesPatterns = listOf(),
                resolvedScenarioNames = listOf(),
                dataComponents = listOf()
            )
            val reportServiceImpl = buildReportService()
            coEvery { reportRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference(refEq("my-tenant")) } returns 123L
            coEvery { userRepository.findIdByUsername(refEq("the-user")) } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name")
                )
            } returns false
            coEvery { reportConverter.convertToModel(reportEntity) } returns report

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(displayName = "report-name")

            // when
            val result = reportServiceImpl.create(
                tenant = "my-tenant",
                creator = "the-user",
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )

            // then
            assertThat(result).all {
                prop(Report::reference).isEqualTo("the-reference")
                prop(Report::creator).isEqualTo("the-user")
                prop(Report::displayName).isEqualTo("report-name")
                prop(Report::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(Report::campaignKeys).isEmpty()
                prop(Report::campaignNamesPatterns).isEmpty()
                prop(Report::resolvedCampaigns).isEmpty()
                prop(Report::scenarioNamesPatterns).isEmpty()
                prop(Report::resolvedScenarioNames).isEmpty()
                prop(Report::dataComponents).isEmpty()
            }
            coVerify {
                reportRepository.save(
                    withArg {
                        assertThat(it).all {
                            prop(ReportEntity::id).isEqualTo(-1)
                            prop(ReportEntity::reference).isEqualTo("the-reference")
                            prop(ReportEntity::tenantId).isEqualTo(123L)
                            prop(ReportEntity::creatorId).isEqualTo(456L)
                            prop(ReportEntity::displayName).isEqualTo("report-name")
                            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.READONLY)
                            prop(ReportEntity::campaignKeys).isEmpty()
                            prop(ReportEntity::campaignNamesPatterns).isEmpty()
                            prop(ReportEntity::scenarioNamesPatterns).isEmpty()
                            prop(ReportEntity::dataComponents).isEmpty()
                        }
                    }
                )
            }
            coVerifyOrder {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(refEq("my-tenant"), refEq("report-name"))
                idGenerator.short()
                tenantRepository.findIdByReference(refEq("my-tenant"))
                userRepository.findIdByUsername(refEq("the-user"))
                reportRepository.save(any())
                reportConverter.convertToModel(reportEntity)
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should create the report by specifying all fields`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        val reportEntity = ReportEntity(
            reference = "report-ref",
            tenantId = 123L,
            creatorId = 456L,
            displayName = "report-name",
            sharingMode = SharingMode.NONE,
            campaignKeys = listOf("campaign-key1", "campaign-key2"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            dataComponents = listOf(
                ReportDataComponentEntity(
                    id = 1,
                    reportId = -1,
                    type = DataComponentType.DIAGRAM,
                    dataSeries = listOf(dataSeries[0])
                ),
                ReportDataComponentEntity(
                    id = 2,
                    reportId = -1,
                    type = DataComponentType.DATA_TABLE,
                    dataSeries = listOf(dataSeries[1])
                )
            )
        )
        val dataSeries1 = relaxedMockk<DataSeries>()
        val dataSeries2 = relaxedMockk<DataSeries>()
        val report = Report(
            reference = "report-ref",
            version = Instant.EPOCH,
            creator = "the-user",
            displayName = "report-name",
            sharingMode = SharingMode.NONE,
            campaignKeys = listOf("campaign-key1", "campaign-key2"),
            campaignNamesPatterns = listOf("*", "\\w"),
            resolvedCampaigns = listOf(
                CampaignKeyAndName("campaign-key1", "campaign-name1"),
                CampaignKeyAndName("campaign-key2", "campaign-name2"),
                CampaignKeyAndName("campaign-key3", "campaign-name3")
            ),
            scenarioNamesPatterns = listOf("\\w"),
            resolvedScenarioNames = listOf("scenario-1", "scenario-2", "scenario-3"),
            dataComponents = listOf(
                Diagram(listOf(dataSeries1)),
                DataTable(listOf(dataSeries2))
            )
        )
        coEvery {
            reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                refEq("my-tenant"),
                refEq("report-name")
            )
        } returns false
        coEvery { reportRepository.save(any()) } returnsArgument 0
        coEvery { tenantRepository.findIdByReference(refEq("my-tenant")) } returns 123L
        coEvery { userRepository.findIdByUsername(refEq("the-user")) } returns 456L
        coEvery {
            campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1", "campaign-key2"))
        } returns setOf("campaign-key1", "campaign-key2")
        coEvery {
            dataSeriesRepository.checkExistenceByTenantAndReference(
                refEq("my-tenant"),
                refEq("series-ref-1")
            )
        } returns true
        coEvery {
            dataSeriesRepository.checkExistenceByTenantAndReference(
                refEq("my-tenant"),
                refEq("series-ref-2")
            )
        } returns true
        coEvery {
            dataSeriesRepository.findAllByTenantAndReferences(refEq("my-tenant"), listOf("series-ref-1"))
        } returns listOf(dataSeries[0])
        coEvery {
            dataSeriesRepository.findAllByTenantAndReferences(refEq("my-tenant"), listOf("series-ref-2"))
        } returns listOf(dataSeries[1])
        coEvery { reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>()) } returns flowOf(
            ReportDataComponentEntity(
                id = 1,
                reportId = -1,
                type = DataComponentType.DIAGRAM,
                dataSeries = listOf(dataSeries[0])
            ),
            ReportDataComponentEntity(
                id = 2,
                reportId = -1,
                type = DataComponentType.DATA_TABLE,
                dataSeries = listOf(dataSeries[1])
            )
        )
        coEvery { idGenerator.short() } returns "report-ref"
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        coEvery { reportConverter.convertToModel(reportEntity) } returns report

        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "report-name",
            sharingMode = SharingMode.NONE,
            campaignKeys = listOf("campaign-key1", "campaign-key2"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            dataComponents = listOf(
                DiagramCreationAndUpdateRequest(dataSeriesReferences = listOf("series-ref-1")),
                DataTableCreationAndUpdateRequest(dataSeriesReferences = listOf("series-ref-2"))
            )
        )

        // when
        val result = reportServiceImpl.create(
            tenant = "my-tenant",
            creator = "the-user",
            reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
        )

        // then
        assertThat(result).all {
            prop(Report::reference).isEqualTo("report-ref")
            prop(Report::creator).isEqualTo("the-user")
            prop(Report::displayName).isEqualTo("report-name")
            prop(Report::sharingMode).isEqualTo(SharingMode.NONE)
            prop(Report::campaignKeys).hasSize(2)
            prop(Report::campaignNamesPatterns).hasSize(2)
            prop(Report::resolvedCampaigns).hasSize(3)
            prop(Report::scenarioNamesPatterns).hasSize(1)
            prop(Report::resolvedScenarioNames).hasSize(3)
            prop(Report::dataComponents).hasSize(2)
        }

        assertThat(result.dataComponents[0] as Diagram).all {
            prop(Diagram::type).isEqualTo(Diagram.TYPE)
            prop(Diagram::datas).hasSize(1)
        }
        assertThat(result.dataComponents[1] as DataTable).all {
            prop(DataTable::type).isEqualTo(DataTable.TYPE)
        }
        coVerify {
            reportRepository.save(
                withArg {
                    assertThat(it).all {
                        prop(ReportEntity::id).isEqualTo(-1)
                        prop(ReportEntity::reference).isEqualTo("report-ref")
                        prop(ReportEntity::tenantId).isEqualTo(123L)
                        prop(ReportEntity::creatorId).isEqualTo(456L)
                        prop(ReportEntity::displayName).isEqualTo("report-name")
                        prop(ReportEntity::sharingMode).isEqualTo(SharingMode.NONE)
                        prop(ReportEntity::campaignKeys).hasSize(2)
                        prop(ReportEntity::campaignNamesPatterns).hasSize(2)
                        prop(ReportEntity::scenarioNamesPatterns).hasSize(1)
                        prop(ReportEntity::dataComponents).isEmpty()
                    }
                }
            )
        }
        coVerifyOrder {
            reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(refEq("my-tenant"), refEq("report-name"))
            campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1", "campaign-key2"))
            dataSeriesRepository.checkExistenceByTenantAndReference(
                tenant = refEq("my-tenant"),
                reference = refEq("series-ref-1")
            )
            dataSeriesRepository.checkExistenceByTenantAndReference(
                tenant = refEq("my-tenant"),
                reference = refEq("series-ref-2")
            )
            idGenerator.short()
            tenantRepository.findIdByReference(refEq("my-tenant"))
            userRepository.findIdByUsername(refEq("the-user"))
            reportRepository.save(any())
            dataSeriesRepository.findAllByTenantAndReferences(
                tenant = refEq("my-tenant"),
                references = listOf("series-ref-1")
            )
            dataSeriesRepository.findAllByTenantAndReferences(
                tenant = refEq("my-tenant"),
                references = listOf("series-ref-2")
            )
            reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
            reportConverter.convertToModel(reportEntity)
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should not create the report with a list of campaign keys that do not belong to the tenant`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            coEvery {
                campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1"))
            } returns emptySet()
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name")
                )
            } returns false

            val reportCreationAndUpdateRequest =
                ReportCreationAndUpdateRequest(displayName = "report-name", campaignKeys = listOf("campaign-key1"))

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.create(
                    tenant = "my-tenant",
                    creator = "the-user",
                    reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
                )
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_CAMPAIGN_KEYS_NOT_ALLOWED)
            coVerifyOrder {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name"),
                    -1
                )
                campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1"))
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should not create the report when reports do not belong to the tenant`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            coEvery { dataSeriesRepository.checkExistenceByTenantAndReference(refEq("my-tenant"), any()) } returns false
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name")
                )
            } returns false

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
                displayName = "report-name",
                sharingMode = SharingMode.NONE,
                campaignKeys = emptyList(),
                campaignNamesPatterns = listOf("*"),
                scenarioNamesPatterns = listOf("\\w"),
                dataComponents = listOf(
                    DiagramCreationAndUpdateRequest(dataSeriesReferences = listOf("series-ref-1"))
                )
            )

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.create(
                    tenant = "my-tenant",
                    creator = "the-user",
                    reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
                )
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_DATA_SERIES_NOT_ALLOWED)
            coVerifyOrder {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name"),
                    -1
                )
                dataSeriesRepository.checkExistenceByTenantAndReference(
                    tenant = refEq("my-tenant"),
                    reference = refEq("series-ref-1")
                )
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should not create the report when an existing report with the same display name exists`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            coEvery { reportRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference(refEq("my-tenant")) } returns 123L
            coEvery { userRepository.findIdByUsername(refEq("the-user")) } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name")
                )
            } returns true

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(displayName = "report-name")

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.create(
                    tenant = "my-tenant",
                    creator = "the-user",
                    reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
                )
            }

            // then
            assertThat(exception.message).isEqualTo("A report named report-name already exists in your organization")
            coVerifyOrder {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(refEq("my-tenant"), refEq("report-name"))
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should get the report if shared`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        val reportEntity = relaxedMockk<ReportEntity>()
        val report = relaxedMockk<Report>()
        coEvery {
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        } returns reportEntity
        coEvery { userRepository.findIdByUsername(refEq("other-user")) } returns 456L
        coEvery { reportConverter.convertToModel(refEq(reportEntity)) } returns report

        // when
        val result = reportServiceImpl.get(tenant = "my-tenant", username = "other-user", reference = "report-ref")

        // then
        assertThat(result).isDataClassEqualTo(report)

        coVerifyOrder {
            userRepository.findIdByUsername(refEq("other-user"))
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
            reportConverter.convertToModel(any())
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should get the report if not shared but owned`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        val reportEntity = relaxedMockk<ReportEntity>()
        val report = relaxedMockk<Report>()
        coEvery {
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        } returns reportEntity
        coEvery { userRepository.findIdByUsername(refEq("the-user")) } returns 456L
        coEvery { reportConverter.convertToModel(refEq(reportEntity)) } returns report

        // when
        val result = reportServiceImpl.get(tenant = "my-tenant", username = "the-user", reference = "report-ref")

        // then
        assertThat(result).isDataClassEqualTo(report)

        coVerifyOrder {
            userRepository.findIdByUsername(refEq("the-user"))
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
            reportConverter.convertToModel(any())
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should not get the report if not shared and not owned`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        val creatorId = 456L
        coEvery {
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        } returns null
        coEvery { userRepository.findIdByUsername(refEq("the-user")) } returns 456L

        // when
        val exception = assertThrows<java.lang.IllegalArgumentException> {
            reportServiceImpl.get(tenant = "my-tenant", username = "the-user", reference = "report-ref")
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_FETCH_DENY)

        coVerifyOrder {
            userRepository.findIdByUsername(refEq("the-user"))
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should not get the report if the user do not belongs to the tenant and OR or the report reference is not found`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            coEvery {
                reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            } returns null
            coEvery { userRepository.findIdByUsername(refEq("the-user")) } returns 456L

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.get(tenant = "my-tenant", username = "the-user", reference = "report-ref")
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_FETCH_DENY)

            coVerifyOrder {
                userRepository.findIdByUsername(refEq("the-user"))
                reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should update the report when shared in write mode and save if there are changes`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            val reportEntity = ReportEntity(
                reference = "report-ref",
                tenantId = 123L,
                creatorId = 456L,
                displayName = "report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("campaign-key1", "campaign-key2"),
                campaignNamesPatterns = listOf("*"),
                dataComponents = listOf(
                    ReportDataComponentEntity(
                        reportId = -1,
                        type = DataComponentType.DIAGRAM,
                        dataSeries = dataSeries
                    )
                )
            )
            val updatedReport = ReportEntity(
                reference = "report-ref",
                tenantId = 123L,
                creatorId = 456L,
                displayName = "new-report-name",
                sharingMode = SharingMode.NONE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*", "\\w"),
                scenarioNamesPatterns = listOf("\\w"),
                dataComponents = listOf(
                    ReportDataComponentEntity(
                        id = 1,
                        reportId = -1,
                        type = DataComponentType.DATA_TABLE,
                        dataSeries = listOf(dataSeries[0])
                    )
                )
            )
            val dataSeries1 = DataSeries(dataSeries[0], "the-user")
            val report = Report(
                reference = "report-ref",
                version = Instant.EPOCH,
                creator = "the-user",
                displayName = "new-report-name",
                sharingMode = SharingMode.NONE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*", "\\w"),
                resolvedCampaigns = listOf(
                    CampaignKeyAndName("campaign-key1", "campaign-name1"),
                    CampaignKeyAndName("campaign-key2", "campaign-name2"),
                    CampaignKeyAndName("campaign-key3", "campaign-name3")
                ),
                scenarioNamesPatterns = listOf("\\w"),
                resolvedScenarioNames = listOf("scenario-1", "scenario-2", "scenario-3"),
                dataComponents = listOf(
                    DataTable(listOf(dataSeries1))
                )
            )
            coEvery {
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            } returns reportEntity
            coEvery { userRepository.findUsernameById(456L) } returns "the-user"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("new-report-name"),
                    -1
                )
            } returns false
            coEvery { userRepository.findIdByUsername(refEq("the-user")) } returns 456L
            coEvery {
                campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1"))
            } returns setOf("campaign-key1")
            coEvery {
                dataSeriesRepository.checkExistenceByTenantAndReference(
                    refEq("my-tenant"),
                    refEq("series-ref-1")
                )
            } returns true

            coEvery {
                dataSeriesRepository.findAllByTenantAndReferences(refEq("my-tenant"), listOf("series-ref-1"))
            } returns listOf(dataSeries[0])
            coEvery { reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>()) } returns flowOf(
                ReportDataComponentEntity(
                    id = 1,
                    reportId = -1,
                    type = DataComponentType.DATA_TABLE,
                    dataSeries = listOf(dataSeries[0])
                )
            )
            coJustRun { reportDataComponentRepository.deleteByReportId(-1L) }
            coEvery { reportRepository.update(any()) } returnsArgument 0
            coEvery { reportConverter.convertToModel(updatedReport) } returns report

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
                displayName = "new-report-name",
                sharingMode = SharingMode.NONE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*", "\\w"),
                scenarioNamesPatterns = listOf("\\w"),
                dataComponents = listOf(DataTableCreationAndUpdateRequest(dataSeriesReferences = listOf("series-ref-1")))
            )

            // when
            val result = reportServiceImpl.update(
                tenant = "my-tenant",
                username = "the-user",
                reference = "report-ref",
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )

            // then
            assertThat(result).all {
                prop(Report::reference).isEqualTo("report-ref")
                prop(Report::creator).isEqualTo("the-user")
                prop(Report::displayName).isEqualTo("new-report-name")
                prop(Report::sharingMode).isEqualTo(SharingMode.NONE)
                prop(Report::campaignKeys).all {
                    hasSize(1)
                    containsOnly("campaign-key1")
                }
                prop(Report::campaignNamesPatterns).all {
                    hasSize(2)
                    containsOnly("*", "\\w")
                }
                prop(Report::resolvedCampaigns).all {
                    hasSize(3)
                    containsOnly(
                        CampaignKeyAndName("campaign-key1", "campaign-name1"),
                        CampaignKeyAndName("campaign-key2", "campaign-name2"),
                        CampaignKeyAndName("campaign-key3", "campaign-name3")
                    )
                }
                prop(Report::scenarioNamesPatterns).all {
                    hasSize(1)
                    containsOnly("\\w")
                }
                prop(Report::resolvedScenarioNames).all {
                    hasSize(3)
                    containsOnly("scenario-1", "scenario-2", "scenario-3")
                }
                prop(Report::dataComponents).all {
                    hasSize(1)
                    index(0).all {
                        isEqualTo(
                            DataTable(datas = listOf(DataSeries(dataSeries[0], "the-user")))
                        )
                    }
                }
            }
            coVerify {
                reportRepository.update(
                    withArg {
                        assertThat(it).all {
                            prop(ReportEntity::id).isEqualTo(-1)
                            prop(ReportEntity::reference).isEqualTo("report-ref")
                            prop(ReportEntity::tenantId).isEqualTo(123L)
                            prop(ReportEntity::creatorId).isEqualTo(456L)
                            prop(ReportEntity::displayName).isEqualTo("new-report-name")
                            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.NONE)
                            prop(ReportEntity::campaignKeys).hasSize(1)
                            prop(ReportEntity::campaignNamesPatterns).hasSize(2)
                            prop(ReportEntity::scenarioNamesPatterns).hasSize(1)
                            prop(ReportEntity::dataComponents).isEmpty()
                        }
                    }
                )
            }
            coVerifyOrder {
                userRepository.findIdByUsername(refEq("the-user"))
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("new-report-name"),
                    -1
                )
                campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1"))
                dataSeriesRepository.checkExistenceByTenantAndReference(
                    tenant = refEq("my-tenant"),
                    reference = refEq("series-ref-1")
                )
                reportDataComponentRepository.deleteByReportId(-1L)
                reportRepository.update(any())
                dataSeriesRepository.findAllByTenantAndReferences(
                    tenant = refEq("my-tenant"),
                    references = listOf("series-ref-1")
                )
                reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
                reportConverter.convertToModel(updatedReport)
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should do nothing when updating a report shared in write mode without change`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            val reportEntity = ReportEntity(
                reference = "report-ref",
                tenantId = 123L,
                creatorId = 456L,
                displayName = "report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*")
            )
            val report = Report(
                reference = "report-ref",
                version = Instant.EPOCH,
                creator = "the-user",
                displayName = "report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*"),
                resolvedCampaigns = listOf(
                    CampaignKeyAndName("campaign-key1", "campaign-name1"),
                    CampaignKeyAndName("campaign-key2", "campaign-name2"),
                    CampaignKeyAndName("campaign-key3", "campaign-name3")
                )
            )
            coEvery {
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            } returns reportEntity
            coEvery { userRepository.findUsernameById(456L) } returns "the-user"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name"),
                    -1
                )
            } returns false
            coEvery { userRepository.findIdByUsername("the-user") } returns 456L
            coEvery {
                campaignRepository.findKeyByTenantAndKeyIn(
                    refEq("my-tenant"),
                    listOf("campaign-key1")
                )
            } returns setOf(
                "campaign-key1"
            )
            coEvery { dataSeriesRepository.checkExistenceByTenantAndReference(refEq("my-tenant"), any()) } returns true
            coEvery { dataSeriesRepository.findByTenantAndReference(refEq("my-tenant"), any()) } returns dataSeries[1]
            coEvery { reportConverter.convertToModel(reportEntity) } returns report

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
                displayName = "report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*")
            )

            // when
            val result = reportServiceImpl.update(
                tenant = "my-tenant",
                username = "the-user",
                reference = "report-ref",
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )

            // then
            assertThat(result).all {
                prop(Report::reference).isEqualTo("report-ref")
                prop(Report::creator).isEqualTo("the-user")
                prop(Report::displayName).isEqualTo("report-name")
                prop(Report::sharingMode).isEqualTo(SharingMode.WRITE)
                prop(Report::campaignKeys).hasSize(1)
                prop(Report::campaignNamesPatterns).hasSize(1)
                prop(Report::resolvedCampaigns).hasSize(3)
                prop(Report::scenarioNamesPatterns).hasSize(0)
                prop(Report::resolvedScenarioNames).hasSize(0)
                prop(Report::dataComponents).hasSize(0)
            }
            coVerifyOrder {
                userRepository.findIdByUsername(refEq("the-user"))
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name"),
                    -1
                )
                campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1"))
                reportConverter.convertToModel(reportEntity)
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should not update a report with a list of campaign keys that do not belong to the tenant`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            val reportEntity = ReportEntity(
                reference = "report-ref",
                tenantId = 123L,
                creatorId = 456L,
                displayName = "report-name"
            )
            coEvery {
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            } returns reportEntity
            coEvery { userRepository.findUsernameById(456L) } returns "the-user"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name"),
                    -1
                )
            } returns false
            coEvery { userRepository.findIdByUsername("the-user") } returns 456L
            coEvery {
                campaignRepository.findKeyByTenantAndKeyIn(
                    refEq("my-tenant"),
                    listOf("campaign-key1")
                )
            } returns emptySet()

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
                displayName = "report-name",
                campaignKeys = listOf("campaign-key1")
            )

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.update(
                    tenant = "my-tenant",
                    username = "the-user",
                    reference = "report-ref",
                    reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
                )
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_CAMPAIGN_KEYS_NOT_ALLOWED)

            coVerifyOrder {
                userRepository.findIdByUsername(refEq("the-user"))
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name"),
                    -1
                )
                campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1"))
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should update the report when not shared and owned`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        val reportEntity = ReportEntity(
            reference = "report-ref",
            tenantId = 123L,
            creatorId = 456L,
            displayName = "report-name",
            sharingMode = SharingMode.NONE,
            campaignKeys = listOf("campaign-key1"),
            campaignNamesPatterns = listOf("*"),
            dataComponents = listOf()
        )
        val updatedReport = ReportEntity(
            reference = "report-ref",
            tenantId = 123L,
            creatorId = 456L,
            displayName = "new-report-name",
            sharingMode = SharingMode.READONLY,
            campaignKeys = listOf("campaign-key1"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            dataComponents = listOf(
                ReportDataComponentEntity(
                    id = 1,
                    reportId = -1,
                    type = DataComponentType.DIAGRAM,
                    dataSeries = listOf(dataSeries[1])
                )
            )
        )
        val dataSeries1 = DataSeries(dataSeries[1], "the-user")
        val report = Report(
            reference = "report-ref",
            version = Instant.EPOCH,
            creator = "the-user",
            displayName = "new-report-name",
            sharingMode = SharingMode.READONLY,
            campaignKeys = listOf("campaign-key1"),
            campaignNamesPatterns = listOf("*", "\\w"),
            resolvedCampaigns = listOf(
                CampaignKeyAndName("campaign-key1", "campaign-name1"),
                CampaignKeyAndName("campaign-key2", "campaign-name2"),
                CampaignKeyAndName("campaign-key3", "campaign-name3")
            ),
            scenarioNamesPatterns = listOf("\\w"),
            resolvedScenarioNames = listOf("scenario-1", "scenario-2", "scenario-3"),
            dataComponents = listOf(
                Diagram(listOf(dataSeries1))
            )
        )
        coEvery {
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        } returns reportEntity
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        coEvery {
            reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                refEq("my-tenant"),
                refEq("new-report-name"),
                -1
            )
        } returns false
        coEvery { userRepository.findIdByUsername("the-user") } returns 456L
        coEvery {
            campaignRepository.findKeyByTenantAndKeyIn(
                refEq("my-tenant"),
                listOf("campaign-key1")
            )
        } returns setOf("campaign-key1")
        coEvery { dataSeriesRepository.checkExistenceByTenantAndReference(refEq("my-tenant"), any()) } returns true
        coEvery {
            dataSeriesRepository.findAllByTenantAndReferences(
                refEq("my-tenant"),
                listOf("series-ref-2")
            )
        } returns listOf(dataSeries[1])
        coEvery { reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>()) } returns flowOf(
            ReportDataComponentEntity(
                id = 1,
                reportId = -1,
                type = DataComponentType.DIAGRAM,
                dataSeries = listOf(dataSeries[1])
            )
        )
        coJustRun { reportDataComponentRepository.deleteByReportId(any()) }
        coEvery { reportRepository.update(any()) } returnsArgument 0
        coEvery { reportConverter.convertToModel(updatedReport) } returns report

        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "new-report-name",
            sharingMode = SharingMode.READONLY,
            campaignKeys = listOf("campaign-key1"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            dataComponents = listOf(DiagramCreationAndUpdateRequest(dataSeriesReferences = listOf("series-ref-2")))
        )

        // when
        val result = reportServiceImpl.update(
            tenant = "my-tenant",
            username = "the-user",
            reference = "report-ref",
            reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
        )

        // then
        assertThat(result).all {
            prop(Report::reference).isEqualTo("report-ref")
            prop(Report::creator).isEqualTo("the-user")
            prop(Report::displayName).isEqualTo("new-report-name")
            prop(Report::sharingMode).isEqualTo(SharingMode.READONLY)
            prop(Report::campaignKeys).all {
                hasSize(1)
                containsOnly("campaign-key1")
            }
            prop(Report::campaignNamesPatterns).all {
                hasSize(2)
                containsOnly("*", "\\w")
            }
            prop(Report::resolvedCampaigns).all {
                hasSize(3)
                containsOnly(
                    CampaignKeyAndName("campaign-key1", "campaign-name1"),
                    CampaignKeyAndName("campaign-key2", "campaign-name2"),
                    CampaignKeyAndName("campaign-key3", "campaign-name3")
                )
            }
            prop(Report::scenarioNamesPatterns).all {
                hasSize(1)
                containsOnly("\\w")
            }
            prop(Report::resolvedScenarioNames).all {
                hasSize(3)
                containsOnly("scenario-1", "scenario-2", "scenario-3")
            }
            prop(Report::dataComponents).all {
                hasSize(1)
                index(0).all {
                    isEqualTo(
                        Diagram(datas = listOf(DataSeries(dataSeries[1], "the-user")))
                    )
                }
            }
        }
        coVerify {
            reportRepository.update(
                withArg {
                    assertThat(it).all {
                        prop(ReportEntity::id).isEqualTo(-1)
                        prop(ReportEntity::reference).isEqualTo("report-ref")
                        prop(ReportEntity::tenantId).isEqualTo(123L)
                        prop(ReportEntity::creatorId).isEqualTo(456L)
                        prop(ReportEntity::displayName).isEqualTo("new-report-name")
                        prop(ReportEntity::sharingMode).isEqualTo(SharingMode.READONLY)
                        prop(ReportEntity::campaignKeys).hasSize(1)
                        prop(ReportEntity::campaignNamesPatterns).hasSize(2)
                        prop(ReportEntity::scenarioNamesPatterns).hasSize(1)
                        prop(ReportEntity::dataComponents).isEmpty()
                    }
                }
            )
        }
        coVerifyOrder {
            userRepository.findIdByUsername(refEq("the-user"))
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
            reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                refEq("my-tenant"),
                refEq("new-report-name"),
                -1
            )
            campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1"))
            dataSeriesRepository.checkExistenceByTenantAndReference(
                tenant = refEq("my-tenant"),
                reference = refEq("series-ref-2")
            )
            reportDataComponentRepository.deleteByReportId(-1L)
            dataSeriesRepository.findAllByTenantAndReferences(
                tenant = refEq("my-tenant"),
                references = listOf("series-ref-2")
            )
            reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
            reportConverter.convertToModel(updatedReport)
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should not update the report when not shared in write mode and not owned`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            coEvery {
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            } returns null
            coEvery { userRepository.findIdByUsername("the-user") } returns 456L

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
                displayName = "new-report-name",
            )

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.update(
                    tenant = "my-tenant",
                    username = "the-user",
                    reference = "report-ref",
                    reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
                )
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_UPDATE_DENY)

            coVerifyOrder {
                userRepository.findIdByUsername(refEq("the-user"))
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should not update the report when not shared and not owned`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        coEvery {
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 123L
            )
        } returns null
        coEvery { userRepository.findIdByUsername("other-user") } returns 123L

        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "new-report-name",
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            reportServiceImpl.update(
                tenant = "my-tenant",
                username = "other-user",
                reference = "report-ref",
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_UPDATE_DENY)

        coVerifyOrder {
            userRepository.findIdByUsername(refEq("other-user"))
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 123L
            )
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should delete the report when shared in write mode`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        val reportEntity = ReportEntity(
            reference = "report-ref",
            tenantId = -1,
            creatorId = 456L,
            displayName = "report-name",
            sharingMode = SharingMode.WRITE,
            campaignNamesPatterns = listOf("*"),
        )
        coEvery { reportRepository.delete(any()) } returns 1
        coEvery {
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 123L
            )
        } returns reportEntity
        coEvery { userRepository.findIdByUsername("other-user") } returns 123L

        // when
        reportServiceImpl.delete(tenant = "my-tenant", username = "other-user", reference = "report-ref")

        // then
        coVerifyOrder {
            userRepository.findIdByUsername("other-user")
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 123L
            )
            reportRepository.delete(reportEntity)
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should delete the report when owned if not shared`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        val reportEntity = ReportEntity(
            reference = "report-ref",
            tenantId = -1,
            creatorId = 456L,
            displayName = "report-name",
            sharingMode = SharingMode.NONE,
            campaignNamesPatterns = listOf("*"),
        )
        coEvery {
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        } returns reportEntity
        coEvery { reportRepository.delete(any()) } returns 1
        coEvery { userRepository.findIdByUsername("the-user") } returns 456L

        // when
        reportServiceImpl.delete(tenant = "my-tenant", username = "the-user", reference = "report-ref")

        // then
        coVerifyOrder {
            userRepository.findIdByUsername("the-user")
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
            reportRepository.delete(reportEntity)
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should not delete the report when not owned not shared in write mode`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            coEvery {
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 123L
                )
            } returns null
            coEvery { userRepository.findIdByUsername("other-user") } returns 123L
            coEvery { reportRepository.delete(any()) } returns 1

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.delete(tenant = "my-tenant", username = "other-user", reference = "report-ref")
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_DELETE_DENY)

            coVerifyOrder {
                userRepository.findIdByUsername("other-user")
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 123L
                )
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should not delete the report when not owned if not shared`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        val creatorId = 456L
        coEvery {
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        } returns null
        coEvery { userRepository.findIdByUsername("other-user") } returns 456L

        // when
        val exception = assertThrows<IllegalArgumentException> {
            reportServiceImpl.delete(tenant = "my-tenant", username = "other-user", reference = "report-ref")
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_DELETE_DENY)

        coVerifyOrder {
            userRepository.findIdByUsername("other-user")
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        }
        confirmVerified(
            reportRepository,
            tenantRepository,
            userRepository,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should return the searched reports from the repository with default sorting and no filter`() =
        testDispatcherProvider.run {
            // given
            val reportServiceImpl = buildReportService()
            val reportEntity1 = relaxedMockk<ReportEntity>()
            val reportEntity2 = relaxedMockk<ReportEntity>()
            val report1 = relaxedMockk<Report>()
            val report2 = relaxedMockk<Report>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("displayName")))
            val page = Page.of(listOf(reportEntity1, reportEntity2), pageable, 2)
            coEvery { reportRepository.searchReports(refEq("my-tenant"), "user", pageable) } returns page
            coEvery { reportConverter.convertToModel(any()) } returns report1 andThen report2

            // when
            val result = reportServiceImpl.search("my-tenant", "user", emptyList(), null, 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Report>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Report>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Report>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Report>::elements).all {
                    hasSize(2)
                    containsExactly(report1, report2)
                }
            }
            coVerifyOrder {
                reportRepository.searchReports(refEq("my-tenant"), "user", pageable)
                reportConverter.convertToModel(refEq(reportEntity1))
                reportConverter.convertToModel(refEq(reportEntity2))
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should return the searched reports from the repository with sorting asc`() =
        testDispatcherProvider.run {
            // given
            val reportServiceImpl = buildReportService()
            val report1 = relaxedMockk<Report>()
            val report2 = relaxedMockk<Report>()
            val reportEntity1 = relaxedMockk<ReportEntity>()
            val reportEntity2 = relaxedMockk<ReportEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("campaignKeys")))
            val page = Page.of(listOf(reportEntity1, reportEntity2), pageable, 2)
            coEvery { reportRepository.searchReports(refEq("my-tenant"), "user", pageable) } returns page
            coEvery { reportConverter.convertToModel(any()) } returns report1 andThen report2

            // when
            val result = reportServiceImpl.search("my-tenant", "user", emptyList(), "campaignKeys:asc", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Report>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Report>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Report>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Report>::elements).all {
                    hasSize(2)
                    containsExactly(report1, report2)
                }
            }
            coVerifyOrder {
                reportRepository.searchReports(refEq("my-tenant"), "user", pageable)
                reportConverter.convertToModel(refEq(reportEntity1))
                reportConverter.convertToModel(refEq(reportEntity2))
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should return the searched reports from the repository with sorting desc`() =
        testDispatcherProvider.run {
            // given
            val reportServiceImpl = buildReportService()
            val report1 = relaxedMockk<Report>()
            val report2 = relaxedMockk<Report>()
            val reportEntity1 = relaxedMockk<ReportEntity>()
            val reportEntity2 = relaxedMockk<ReportEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("campaignKeys")))
            val page = Page.of(listOf(reportEntity2, reportEntity1), pageable, 2)
            coEvery { reportRepository.searchReports(refEq("my-tenant"), "user", pageable) } returns page
            coEvery { reportConverter.convertToModel(any()) } returns report2 andThen report1

            // when
            val result = reportServiceImpl.search("my-tenant", "user", emptyList(), "campaignKeys:desc", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Report>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Report>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Report>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Report>::elements).all {
                    hasSize(2)
                    containsExactly(report2, report1)
                }
            }
            coVerifyOrder {
                reportRepository.searchReports(refEq("my-tenant"), "user", pageable)
                reportConverter.convertToModel(refEq(reportEntity2))
                reportConverter.convertToModel(refEq(reportEntity1))
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should return the searched reports from the repository with specified filters and default sort`() =
        testDispatcherProvider.run {
            // given
            val reportServiceImpl = buildReportService()
            val filter1 = "%Un%u_%"
            val filter2 = "%u_Er%"
            val reportEntity1 = relaxedMockk<ReportEntity>()
            val reportEntity2 = relaxedMockk<ReportEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("displayName")))
            val page = Page.of(listOf(reportEntity1, reportEntity2), Pageable.from(0, 20), 2)
            coEvery {
                reportRepository.searchReports(
                    refEq("my-tenant"),
                    "user",
                    listOf(filter1, filter2),
                    pageable
                )
            } returns page
            val report1 = relaxedMockk<Report>()
            val report2 = relaxedMockk<Report>()
            coEvery { reportConverter.convertToModel(any()) } returns report1 andThen report2

            // when
            val result =
                reportServiceImpl.search("my-tenant", "user", listOf("Un*u?", "u?Er"), null, 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Report>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Report>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Report>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Report>::elements).all {
                    hasSize(2)
                    containsExactly(report1, report2)
                }
            }
            coVerifyOrder {
                reportRepository.searchReports(refEq("my-tenant"), "user", listOf(filter1, filter2), pageable)
                reportConverter.convertToModel(refEq(reportEntity1))
                reportConverter.convertToModel(refEq(reportEntity2))
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should return the searched reports from the repository with specified sorting and filters`() =
        testDispatcherProvider.run {
            // given
            val reportServiceImpl = buildReportService()
            val filter1 = "%F_oo%"
            val filter2 = "%Us_r%"
            val reportEntity2 = relaxedMockk<ReportEntity>()
            val reportEntity3 = relaxedMockk<ReportEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("sharingMode")))
            val page = Page.of(listOf(reportEntity2, reportEntity3), Pageable.from(0, 20), 2)
            coEvery {
                reportRepository.searchReports(
                    refEq("my-tenant"),
                    "user",
                    listOf(filter1, filter2),
                    pageable
                )
            } returns page
            val report2 = relaxedMockk<Report>()
            val report3 = relaxedMockk<Report>()
            coEvery { reportConverter.convertToModel(any()) } returns report2 andThen report3

            // when
            val result =
                reportServiceImpl.search("my-tenant", "user", listOf("F_oo", "Us_r"), "sharingMode", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Report>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Report>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Report>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Report>::elements).all {
                    hasSize(2)
                    containsExactly(report2, report3)
                }
            }
            coVerifyOrder {
                reportRepository.searchReports("my-tenant", "user", listOf(filter1, filter2), pageable)
                reportConverter.convertToModel(refEq(reportEntity2))
                reportConverter.convertToModel(refEq(reportEntity3))
            }
            confirmVerified(
                reportRepository,
                tenantRepository,
                userRepository,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should process a render and call processTaskGeneration`() =
        testDispatcherProvider.runTest {
            //given
            val spiedReportService = spyk(buildReportService(), recordPrivateCalls = true)
            coEvery {
                reportRepository.findByTenantAndReference(
                    any(),
                    any()
                )
            } returns reportEntity2
            coEvery { idGenerator.short() } returns "report-task-1"
            coEvery { reportTaskRepository.save(any<ReportTaskEntity>()) } returns reportTaskEntity
            val latch = Latch(true)
            coEvery {
                reportGenerator.processTaskGeneration(
                    any<String>(),
                    any<String>(),
                    any<String>(),
                    any<ReportEntity>(),
                    any<ReportTaskEntity>()
                )
            } coAnswers { latch.release() }
            coExcludeRecords { spiedReportService.render("my-tenant", "user", "qoi78wizened") }

            // when
            spiedReportService.render("my-tenant", "user", "qoi78wizened")
            latch.await()

            // then
            coVerifyOnce {
                reportGenerator.processTaskGeneration(
                    "my-tenant",
                    "user",
                    "qoi78wizened",
                    refEq(reportEntity2),
                    refEq(reportTaskEntity)
                )
            }
            confirmVerified(reportGenerator)
        }

    @Test
    fun `should return the report file given the right params`() =
        testDispatcherProvider.run {
            //given
            val reportServiceImpl = buildReportService()
            val file = File.createTempFile(reportEntity.displayName, ".pdf")
            file.writeText(
                """
                    Report of ${reportEntity.displayName}
                    Lorem ipsum dolor conot foo bar
                """.trimIndent()
            )
            val reportFileBytes = file.readBytes()
            val reportTaskEntity = ReportTaskEntity(
                creator = "user",
                creationTimestamp = Instant.now(),
                id = -1,
                reportId = -1,
                reference = "report-1",
                tenantReference = "my-tenant",
                status = ReportTaskStatus.COMPLETED,
                updateTimestamp = Instant.now()
            )
            val userEntity = relaxedMockk<UserEntity>()
            val tenantEntity = relaxedMockk<TenantEntity>()
            val reportFileEntity = ReportFileEntity(
                "Report-File",
                reportFileBytes,
                Instant.now(),
                reportTaskEntity.id
            )
            coEvery {
                reportTaskRepository.findByTenantReferenceAndReference(
                    any(),
                    any()
                )
            } returns reportTaskEntity
            coEvery {
                reportFileRepository.retrieveReportFileByTenantAndReference(
                    any(),
                    any(),
                    any()
                )
            } returns reportFileEntity

            //when
            val result = reportServiceImpl.read(
                tenant = tenantEntity.reference,
                username = userEntity.username,
                taskReference = "${reportFileEntity.reportTaskId}"
            )

            //then
            assertThat(result).isEqualTo(DownloadFile("Report-File", reportFileBytes))
            coVerify {
                reportFileRepository.retrieveReportFileByTenantAndReference(
                    tenantEntity.reference,
                    reportFileEntity.reportTaskId,
                    userEntity.username
                )
            }
            confirmVerified(reportFileRepository)
        }

    @Test
    fun `should throw an exception when the report file is null and task status is created`() =
        testDispatcherProvider.run {
            //given
            val reportServiceImpl = buildReportService()
            val reportTaskEntity = ReportTaskEntity(
                creator = "user",
                creationTimestamp = Instant.now(),
                id = -1,
                reportId = -1,
                reference = "report-1",
                tenantReference = "my-tenant",
                status = ReportTaskStatus.COMPLETED,
                updateTimestamp = Instant.now()
            )
            val userEntity = relaxedMockk<UserEntity>()
            val tenantEntity = relaxedMockk<TenantEntity>()
            coEvery {
                reportTaskRepository.findByTenantReferenceAndReference(
                    any(),
                    any()
                )
            } returns reportTaskEntity
            coEvery {
                reportFileRepository.retrieveReportFileByTenantAndReference(
                    any(),
                    any(),
                    any()
                )
            } returns null

            //when
            val caught = assertThrows<IllegalArgumentException> {
                reportServiceImpl.read(
                    tenant = tenantEntity.reference,
                    username = userEntity.username,
                    taskReference = "-1"
                )
            }

            //then
            assertThat(caught).all {
                prop(IllegalArgumentException::message).isEqualTo("File not found")
            }
            coVerify {
                reportFileRepository.retrieveReportFileByTenantAndReference(
                    tenantEntity.reference,
                    -1,
                    userEntity.username
                )
            }
            confirmVerified(reportFileRepository)
        }

    @Test
    fun `should throw an exception for unknown report task reference`() =
        testDispatcherProvider.run {
            //given
            val reportServiceImpl = buildReportService()
            coEvery {
                reportTaskRepository.findByTenantReferenceAndReference(
                    any(),
                    any()
                )
            } returns null
            val userEntity = relaxedMockk<UserEntity>()
            val tenantEntity = relaxedMockk<TenantEntity>()

            // when
            val caught = assertThrows<IllegalArgumentException> {
                reportServiceImpl.read(
                    tenant = tenantEntity.reference,
                    username = userEntity.username,
                    taskReference = "unknown ref"
                )
            }

            //then
            assertThat(caught).all {
                prop(IllegalArgumentException::message).isEqualTo("Requested file not found")
            }
            coVerify {
                reportTaskRepository.findByTenantReferenceAndReference(
                    tenantEntity.reference,
                    "unknown ref",
                )
            }
            confirmVerified(reportTaskRepository)
        }

    @Test
    fun `should throw an exception when file is still processing`() =
        testDispatcherProvider.run {
            //given
            val reportServiceImpl = buildReportService()
            val userEntity = relaxedMockk<UserEntity>()
            val reportTaskEntity = ReportTaskEntity(
                creator = "user",
                creationTimestamp = Instant.now(),
                id = -1,
                reportId = -1,
                reference = "report-1",
                tenantReference = "my-tenant",
                status = ReportTaskStatus.PROCESSING,
                updateTimestamp = Instant.now()
            )
            val tenantEntity = relaxedMockk<TenantEntity>()
            coEvery {
                reportTaskRepository.findByTenantReferenceAndReference(
                    any(),
                    any()
                )
            } returns reportTaskEntity

            // when
            val caught = assertThrows<ExitStatusException> {
                reportServiceImpl.read(
                    tenant = tenantEntity.reference,
                    username = userEntity.username,
                    taskReference = reportTaskEntity.reference
                )
            }

            //then
            assertThat(caught).all {
                prop(ExitStatusException::exitStatus).isEqualTo(102)
                prop(ExitStatusException::message).isEqualTo("java.lang.IllegalArgumentException: File still Processing")
            }
            coVerify {
                reportTaskRepository.findByTenantReferenceAndReference(
                    tenantEntity.reference,
                    reportTaskEntity.reference,
                )
            }
            confirmVerified(reportTaskRepository)
        }

    @Test
    fun `should throw an exception when task status is failure`() =
        testDispatcherProvider.run {
            //given
            val reportServiceImpl = buildReportService()
            val userEntity = relaxedMockk<UserEntity>()
            val reportTaskEntity = ReportTaskEntity(
                creator = "user",
                creationTimestamp = Instant.now(),
                id = -1,
                reportId = -1,
                reference = "report-1",
                tenantReference = "my-tenant",
                status = ReportTaskStatus.FAILED,
                updateTimestamp = Instant.now()
            )
            val tenantEntity = relaxedMockk<TenantEntity>()
            coEvery {
                reportTaskRepository.findByTenantReferenceAndReference(
                    any(),
                    any()
                )
            } returns reportTaskEntity

            // when
            val caught = assertThrows<ReportGenerationException> {
                reportServiceImpl.read(
                    tenant = tenantEntity.reference,
                    username = userEntity.username,
                    taskReference = reportTaskEntity.reference
                )
            }

            //then
            assertThat(caught).all {
                prop(ReportGenerationException::message).isEqualTo("There was an error generating the file: null")
            }
            coVerify {
                reportTaskRepository.findByTenantReferenceAndReference(
                    tenantEntity.reference,
                    reportTaskEntity.reference,
                )
            }
            confirmVerified(reportTaskRepository)
        }

    private fun CoroutineScope.buildReportService() = ReportServiceImpl(
        reportRepository,
        tenantRepository,
        userRepository,
        campaignRepository,
        reportDataComponentRepository,
        dataSeriesRepository,
        idGenerator,
        reportConverter,
        reportTaskRepository,
        reportFileRepository,
        reportGenerator,
        this
    )
}