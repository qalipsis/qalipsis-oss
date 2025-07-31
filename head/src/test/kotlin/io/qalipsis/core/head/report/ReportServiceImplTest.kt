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
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameInstanceAs
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
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
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
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository.CampaignKeyAndName
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.jdbc.repository.ReportDataComponentRepository
import io.qalipsis.core.head.jdbc.repository.ReportFileRepository
import io.qalipsis.core.head.jdbc.repository.ReportRepository
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.model.DataSeries
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
import io.qalipsis.core.head.security.TenantProvider
import io.qalipsis.core.head.security.UserProvider
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

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var reportRepository: ReportRepository

    @MockK
    private lateinit var tenantProvider: TenantProvider

    @MockK
    private lateinit var userProvider: UserProvider

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
    private val reportEntityPrototype = ReportEntity(
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
    private val reportEntityProptotype2: ReportEntity = reportEntityPrototype.copy(dataComponents = emptyList(), id = 7)
    private val reportTaskEntity = ReportTaskEntity(
        creator = "user",
        creationTimestamp = now,
        id = 11,
        reportId = reportEntityProptotype2.id,
        reference = "report-task-1",
        tenantReference = "my-tenant",
        status = ReportTaskStatus.PENDING,
        updateTimestamp = now
    )

    @Test
    internal fun `should create the report with the default sharing mode and empty campaign keys and scenario names patterns`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            val savedEntity = slot<ReportEntity>()
            coEvery { reportRepository.save(capture(savedEntity)) } answers { firstArg<ReportEntity>().copy(id = 123982) }
            coEvery { tenantProvider.findIdByReference(refEq("my-tenant")) } returns 123L
            coEvery { userProvider.findIdByUsername(refEq("the-user")) } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name")
                )
            } returns false
            val convertedEntity = slot<ReportEntity>()
            val convertedReport = mockk<Report>()
            coEvery { reportConverter.convertToModel(capture(convertedEntity)) } returns convertedReport

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(displayName = "report-name")

            // when
            val result = reportServiceImpl.create(
                tenant = "my-tenant",
                creator = "the-user",
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )

            // then
            assertThat(result).isSameInstanceAs(convertedReport)
            coVerifyOrder {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(refEq("my-tenant"), refEq("report-name"))
                idGenerator.short()
                tenantProvider.findIdByReference(refEq("my-tenant"))
                userProvider.findIdByUsername(refEq("the-user"))
                reportRepository.save(capture(savedEntity))
                reportConverter.convertToModel(refEq(convertedEntity.captured))
            }
            assertThat(savedEntity.captured).all {
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
            assertThat(convertedEntity.captured).all {
                isNotSameAs(savedEntity.captured)
                prop(ReportEntity::id).isEqualTo(123982)
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
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
        coEvery {
            reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                refEq("my-tenant"),
                refEq("report-name")
            )
        } returns false
        val savedEntity = slot<ReportEntity>()
        coEvery { reportRepository.save(capture(savedEntity)) } answers { firstArg<ReportEntity>().copy(id = 1782) }
        coEvery { tenantProvider.findIdByReference(refEq("my-tenant")) } returns 123L
        coEvery { userProvider.findIdByUsername(refEq("the-user")) } returns 456L
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
        coEvery { reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>()) } answers {
            flowOf(
                *firstArg<Iterable<ReportDataComponentEntity>>().toList().toTypedArray()
            )
        }
        coEvery { idGenerator.short() } returns "report-ref"
        coEvery { userProvider.findUsernameById(456L) } returns "the-user"
        val convertedEntity = slot<ReportEntity>()
        val convertedReport = mockk<Report>()
        coEvery { reportConverter.convertToModel(capture(convertedEntity)) } returns convertedReport

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
        assertThat(result).isSameInstanceAs(convertedReport)
        val savedReportDataComponent = slot<List<ReportDataComponentEntity>>()
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
            tenantProvider.findIdByReference(refEq("my-tenant"))
            userProvider.findIdByUsername(refEq("the-user"))
            reportRepository.save(refEq(savedEntity.captured))
            dataSeriesRepository.findAllByTenantAndReferences(
                tenant = refEq("my-tenant"),
                references = listOf("series-ref-1")
            )
            dataSeriesRepository.findAllByTenantAndReferences(
                tenant = refEq("my-tenant"),
                references = listOf("series-ref-2")
            )
            reportDataComponentRepository.saveAll(capture(savedReportDataComponent))
            reportConverter.convertToModel(refEq(convertedEntity.captured))
        }
        assertThat(savedEntity.captured).all {
            prop(ReportEntity::id).isEqualTo(-1)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(123L)
            prop(ReportEntity::creatorId).isEqualTo(456L)
            prop(ReportEntity::displayName).isEqualTo("report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.NONE)
            prop(ReportEntity::campaignKeys).all {
                hasSize(2)
                containsOnly("campaign-key1", "campaign-key2")
            }
            prop(ReportEntity::campaignNamesPatterns).all {
                hasSize(2)
                containsOnly("*", "\\w")
            }
            prop(ReportEntity::scenarioNamesPatterns).all {
                hasSize(1)
                containsOnly("\\w")
            }
            prop(ReportEntity::dataComponents).isEmpty()
        }
        assertThat(savedReportDataComponent.captured).all {
            hasSize(2)
            index(0).isDataClassEqualTo(
                ReportDataComponentEntity(
                    -1,
                    DataComponentType.DIAGRAM,
                    1782,
                    listOf(dataSeries[0])
                )
            )
            index(1).isDataClassEqualTo(
                ReportDataComponentEntity(
                    -1,
                    DataComponentType.DATA_TABLE,
                    1782,
                    listOf(dataSeries[1])
                )
            )
        }
        assertThat(convertedEntity.captured).all {
            isNotSameAs(savedEntity.captured)
            prop(ReportEntity::id).isEqualTo(1782)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(123L)
            prop(ReportEntity::creatorId).isEqualTo(456L)
            prop(ReportEntity::displayName).isEqualTo("report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.NONE)
            prop(ReportEntity::campaignKeys).all {
                hasSize(2)
                containsOnly("campaign-key1", "campaign-key2")
            }
            prop(ReportEntity::campaignNamesPatterns).all {
                hasSize(2)
                containsOnly("*", "\\w")
            }
            prop(ReportEntity::scenarioNamesPatterns).all {
                hasSize(1)
                containsOnly("\\w")
            }
            prop(ReportEntity::dataComponents).all {
                hasSize(2)
                index(0).isDataClassEqualTo(
                    ReportDataComponentEntity(
                        -1,
                        DataComponentType.DIAGRAM,
                        reportId = 1782,
                        dataSeries = listOf(dataSeries[0])
                    )
                )
                index(1).isDataClassEqualTo(
                    ReportDataComponentEntity(
                        -1,
                        DataComponentType.DATA_TABLE,
                        reportId = 1782,
                        dataSeries = listOf(dataSeries[1])
                    )
                )
            }
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
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
                tenantProvider,
                userProvider,
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
                tenantProvider,
                userProvider,
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
            coEvery { tenantProvider.findIdByReference(refEq("my-tenant")) } returns 123L
            coEvery { userProvider.findIdByUsername(refEq("the-user")) } returns 456L
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
                tenantProvider,
                userProvider,
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
        coEvery { userProvider.findIdByUsername(refEq("other-user")) } returns 456L
        coEvery { reportConverter.convertToModel(refEq(reportEntity)) } returns report

        // when
        val result = reportServiceImpl.get(tenant = "my-tenant", username = "other-user", reference = "report-ref")

        // then
        assertThat(result).isDataClassEqualTo(report)

        coVerifyOrder {
            userProvider.findIdByUsername(refEq("other-user"))
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
            reportConverter.convertToModel(any())
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
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
        coEvery { userProvider.findIdByUsername(refEq("the-user")) } returns 456L
        coEvery { reportConverter.convertToModel(refEq(reportEntity)) } returns report

        // when
        val result = reportServiceImpl.get(tenant = "my-tenant", username = "the-user", reference = "report-ref")

        // then
        assertThat(result).isDataClassEqualTo(report)

        coVerifyOrder {
            userProvider.findIdByUsername(refEq("the-user"))
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
            reportConverter.convertToModel(any())
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
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
        coEvery {
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        } returns null
        coEvery { userProvider.findIdByUsername(refEq("the-user")) } returns 456L

        // when
        val exception = assertThrows<java.lang.IllegalArgumentException> {
            reportServiceImpl.get(tenant = "my-tenant", username = "the-user", reference = "report-ref")
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_FETCH_DENY)

        coVerifyOrder {
            userProvider.findIdByUsername(refEq("the-user"))
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 456L
            )
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
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
            coEvery { userProvider.findIdByUsername(refEq("the-user")) } returns 456L

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.get(tenant = "my-tenant", username = "the-user", reference = "report-ref")
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_FETCH_DENY)

            coVerifyOrder {
                userProvider.findIdByUsername(refEq("the-user"))
                reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
            ).copy(id = 6165)
            val convertedReport = mockk<Report>()
            coEvery {
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            } returns reportEntity
            coEvery { userProvider.findUsernameById(456L) } returns "the-user"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("new-report-name"),
                    6165
                )
            } returns false
            coEvery { userProvider.findIdByUsername(refEq("the-user")) } returns 456L
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
                    reportId = 6165,
                    type = DataComponentType.DATA_TABLE,
                    dataSeries = listOf(dataSeries[0])
                )
            )
            coJustRun { reportDataComponentRepository.deleteByReportId(6165) }
            val updatedEntity = slot<ReportEntity>()
            val convertedEntity = slot<ReportEntity>()
            coEvery { reportRepository.update(capture(updatedEntity)) } returnsArgument 0
            coEvery { reportConverter.convertToModel(capture(convertedEntity)) } returns convertedReport

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
            assertThat(result).isSameInstanceAs(convertedReport)
            coVerifyOrder {
                userProvider.findIdByUsername(refEq("the-user"))
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("new-report-name"),
                    6165
                )
                campaignRepository.findKeyByTenantAndKeyIn(refEq("my-tenant"), listOf("campaign-key1"))
                dataSeriesRepository.checkExistenceByTenantAndReference(
                    tenant = refEq("my-tenant"),
                    reference = refEq("series-ref-1")
                )
                reportDataComponentRepository.deleteByReportId(6165)
                reportRepository.update(refEq(updatedEntity.captured))
                dataSeriesRepository.findAllByTenantAndReferences(
                    tenant = refEq("my-tenant"),
                    references = listOf("series-ref-1")
                )
                reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
                reportConverter.convertToModel(refEq(convertedEntity.captured))
            }
            assertThat(updatedEntity.captured).all {
                isNotSameAs(reportEntity)
                isDataClassEqualTo(
                    ReportEntity(
                        reference = "report-ref",
                        tenantId = 123L,
                        creatorId = 456L,
                        displayName = "new-report-name",
                        sharingMode = SharingMode.NONE,
                        campaignKeys = listOf("campaign-key1"),
                        campaignNamesPatterns = listOf("*", "\\w"),
                        scenarioNamesPatterns = listOf("\\w"),
                        dataComponents = emptyList()
                    ).copy(id = 6165)
                )
            }
            assertThat(convertedEntity.captured).all {
                isNotSameAs(reportEntity)
                isNotSameAs(updatedEntity)
                isDataClassEqualTo(
                    ReportEntity(
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
                                reportId = 6165,
                                type = DataComponentType.DATA_TABLE,
                                dataSeries = listOf(dataSeries[0])
                            )
                        )
                    ).copy(id = 6165)
                )
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
            coEvery { userProvider.findUsernameById(456L) } returns "the-user"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name"),
                    -1
                )
            } returns false
            coEvery { userProvider.findIdByUsername("the-user") } returns 456L
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
                userProvider.findIdByUsername(refEq("the-user"))
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
                tenantProvider,
                userProvider,
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
            coEvery { userProvider.findUsernameById(456L) } returns "the-user"
            coEvery {
                reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    refEq("my-tenant"),
                    refEq("report-name"),
                    -1
                )
            } returns false
            coEvery { userProvider.findIdByUsername("the-user") } returns 456L
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
                userProvider.findIdByUsername(refEq("the-user"))
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
                tenantProvider,
                userProvider,
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
        coEvery { userProvider.findUsernameById(456L) } returns "the-user"
        coEvery {
            reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                refEq("my-tenant"),
                refEq("new-report-name"),
                -1
            )
        } returns false
        coEvery { userProvider.findIdByUsername("the-user") } returns 456L
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
            userProvider.findIdByUsername(refEq("the-user"))
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
            tenantProvider,
            userProvider,
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
            coEvery { userProvider.findIdByUsername("the-user") } returns 456L

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
                userProvider.findIdByUsername(refEq("the-user"))
                reportRepository.getReportIfUpdatable(
                    tenant = refEq("my-tenant"),
                    reference = refEq("report-ref"),
                    creatorId = 456L
                )
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
        coEvery { userProvider.findIdByUsername("other-user") } returns 123L

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
            userProvider.findIdByUsername(refEq("other-user"))
            reportRepository.getReportIfUpdatable(
                tenant = refEq("my-tenant"),
                reference = refEq("report-ref"),
                creatorId = 123L
            )
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
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
        coEvery { reportRepository.deleteAllByReference(any()) } returns 1
        coEvery {
            reportRepository.getUpdatableReportReferences(
                tenant = refEq("my-tenant"),
                references = eq(setOf("report-ref")),
                creatorId = 123L
            )
        } returns listOf("report-ref")
        coEvery { userProvider.findIdByUsername("other-user") } returns 123L

        // when
        reportServiceImpl.delete(tenant = "my-tenant", username = "other-user", references = setOf("report-ref"))

        // then
        coVerifyOrder {
            userProvider.findIdByUsername("other-user")
            reportRepository.getUpdatableReportReferences(
                tenant = refEq("my-tenant"),
                references = eq(setOf("report-ref")),
                creatorId = 123L
            )
            reportRepository.deleteAllByReference(listOf("report-ref"))
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
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
        coEvery {
            reportRepository.getUpdatableReportReferences(
                tenant = refEq("my-tenant"),
                references = eq(setOf("report-ref")),
                creatorId = 456L
            )
        } returns listOf("report-ref")
        coEvery { reportRepository.deleteAllByReference(any()) } returns 1
        coEvery { userProvider.findIdByUsername("the-user") } returns 456L

        // when
        reportServiceImpl.delete(tenant = "my-tenant", username = "the-user", references = setOf("report-ref"))

        // then
        coVerifyOrder {
            userProvider.findIdByUsername("the-user")
            reportRepository.getUpdatableReportReferences(
                tenant = refEq("my-tenant"),
                references = eq(setOf("report-ref")),
                creatorId = 456L
            )
            reportRepository.deleteAllByReference(listOf("report-ref"))
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
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
                reportRepository.getUpdatableReportReferences(
                    tenant = refEq("my-tenant"),
                    references = eq(setOf("report-ref")),
                    creatorId = 123L
                )
            } returns listOf("other-report-ref")
            coEvery { userProvider.findIdByUsername("other-user") } returns 123L

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.delete(
                    tenant = "my-tenant",
                    username = "other-user",
                    references = setOf("report-ref")
                )
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_DELETE_DENY)

            coVerifyOrder {
                userProvider.findIdByUsername("other-user")
                reportRepository.getUpdatableReportReferences(
                    tenant = refEq("my-tenant"),
                    references = eq(setOf("report-ref")),
                    creatorId = 123L
                )
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
        coEvery {
            reportRepository.getUpdatableReportReferences(
                tenant = refEq("my-tenant"),
                references = eq(setOf("report-ref")),
                creatorId = 456L
            )
        } returns listOf("other-report-ref")
        coEvery { userProvider.findIdByUsername("other-user") } returns 456L

        // when
        val exception = assertThrows<IllegalArgumentException> {
            reportServiceImpl.delete(tenant = "my-tenant", username = "other-user", references = setOf("report-ref"))
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_DELETE_DENY)

        coVerifyOrder {
            userProvider.findIdByUsername("other-user")
            reportRepository.getUpdatableReportReferences(
                tenant = refEq("my-tenant"),
                references = eq(setOf("report-ref")),
                creatorId = 456L
            )
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
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
            val reportEntity1 = relaxedMockk<ReportEntity> {
                every { id } returns 1
            }
            val reportEntity2 = relaxedMockk<ReportEntity> {
                every { id } returns 2
            }
            val report1 = relaxedMockk<Report>()
            val report2 = relaxedMockk<Report>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("displayName")))
            val page = Page.of(listOf(reportEntity1.id, reportEntity2.id), pageable, 2)
            coEvery { reportRepository.searchReports(refEq("my-tenant"), "user", pageable) } returns page
            coEvery { reportConverter.convertToModel(any()) } returns report1 andThen report2
            coEvery { reportRepository.findByIdIn(listOf(1, 2)) } returns listOf(reportEntity1, reportEntity2)

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
                reportRepository.findByIdIn(listOf(1, 2))
                reportConverter.convertToModel(refEq(reportEntity1))
                reportConverter.convertToModel(refEq(reportEntity2))
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
            val reportEntity1 = relaxedMockk<ReportEntity> {
                every { id } returns 1
            }
            val reportEntity2 = relaxedMockk<ReportEntity> {
                every { id } returns 2
            }
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("campaignKeys", true)))
            val page = Page.of(listOf(reportEntity1.id, reportEntity2.id), pageable, 2)
            coEvery { reportRepository.searchReports(refEq("my-tenant"), "user", pageable) } returns page
            coEvery { reportConverter.convertToModel(any()) } returns report1 andThen report2
            coEvery { reportRepository.findByIdIn(listOf(1, 2)) } returns listOf(reportEntity1, reportEntity2)

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
                reportRepository.findByIdIn(listOf(1, 2))
                reportConverter.convertToModel(refEq(reportEntity1))
                reportConverter.convertToModel(refEq(reportEntity2))
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
            val reportEntity1 = relaxedMockk<ReportEntity> {
                every { id } returns 1
            }
            val reportEntity2 = relaxedMockk<ReportEntity> {
                every { id } returns 2
            }
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("campaignKeys", true)))
            val page = Page.of(listOf(reportEntity2.id, reportEntity1.id), pageable, 2)
            coEvery { reportRepository.searchReports(refEq("my-tenant"), "user", pageable) } returns page
            coEvery { reportConverter.convertToModel(any()) } returns report2 andThen report1
            coEvery { reportRepository.findByIdIn(listOf(2, 1)) } returns listOf(reportEntity2, reportEntity1)

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
                reportRepository.findByIdIn(listOf(2, 1))
                reportConverter.convertToModel(refEq(reportEntity2))
                reportConverter.convertToModel(refEq(reportEntity1))
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }

    @Test
    internal fun `should return the searched reports from the repository with sorting desc and also conserve the order of report IDs when mapping to entities`() =
        testDispatcherProvider.run {
            // given
            val reportServiceImpl = buildReportService()
            val report1 = relaxedMockk<Report>()
            val report2 = relaxedMockk<Report>()
            val report4 = relaxedMockk<Report>()
            val report7 = relaxedMockk<Report>()
            val reportEntity1 = relaxedMockk<ReportEntity> {
                every { id } returns 1
            }
            val reportEntity2 = relaxedMockk<ReportEntity> {
                every { id } returns 2
            }
            val reportEntity4 = relaxedMockk<ReportEntity> {
                every { id } returns 4
            }
            val reportEntity7 = relaxedMockk<ReportEntity> {
                every { id } returns 7
            }
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("campaignKeys", true)))
            val page =
                Page.of(listOf(reportEntity4.id, reportEntity2.id, reportEntity1.id, reportEntity7.id), pageable, 2)
            coEvery { reportRepository.searchReports(refEq("my-tenant"), "user", pageable) } returns page
            coEvery { reportConverter.convertToModel(any()) } returns report4 andThen report2 andThen report1 andThen report7
            coEvery { reportRepository.findByIdIn(listOf(4, 2, 1, 7)) } returns listOf(
                reportEntity1,
                reportEntity2,
                reportEntity4,
                reportEntity7
            )

            // when
            val result = reportServiceImpl.search("my-tenant", "user", emptyList(), "campaignKeys:desc", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Report>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Report>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Report>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Report>::elements).all {
                    hasSize(4)
                    containsExactly(report4, report2, report1, report7)
                }
            }
            coVerifyOrder {
                reportRepository.searchReports(refEq("my-tenant"), "user", pageable)
                reportRepository.findByIdIn(listOf(4, 2, 1, 7))
                reportConverter.convertToModel(refEq(reportEntity4))
                reportConverter.convertToModel(refEq(reportEntity2))
                reportConverter.convertToModel(refEq(reportEntity1))
                reportConverter.convertToModel(refEq(reportEntity7))
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
            val reportEntity1 = relaxedMockk<ReportEntity> {
                every { id } returns 1
            }
            val reportEntity2 = relaxedMockk<ReportEntity> {
                every { id } returns 2
            }
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("displayName")))
            val page = Page.of(listOf(reportEntity1.id, reportEntity2.id), Pageable.from(0, 20), 2)
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
            coEvery { reportRepository.findByIdIn(listOf(1, 2)) } returns listOf(reportEntity1, reportEntity2)

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
                reportRepository.findByIdIn(listOf(1, 2))
                reportConverter.convertToModel(refEq(reportEntity1))
                reportConverter.convertToModel(refEq(reportEntity2))
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
            val reportEntity2 = relaxedMockk<ReportEntity> {
                every { id } returns 2
            }
            val reportEntity3 = relaxedMockk<ReportEntity> {
                every { id } returns 3
            }
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("sharingMode", true)))
            val page = Page.of(listOf(reportEntity2.id, reportEntity3.id), Pageable.from(0, 20), 2)
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
            coEvery { reportRepository.findByIdIn(listOf(2, 3)) } returns listOf(reportEntity2, reportEntity3)

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
                reportRepository.findByIdIn(listOf(2, 3))
                reportConverter.convertToModel(refEq(reportEntity2))
                reportConverter.convertToModel(refEq(reportEntity3))
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
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
            } returns reportEntityProptotype2
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
                    refEq(reportEntityProptotype2),
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
            val file = File.createTempFile(reportEntityPrototype.displayName, ".pdf")
            file.writeText(
                """
                    Report of ${reportEntityPrototype.displayName}
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
                tenant = "my-tenant",
                username = "my-user",
                taskReference = "${reportFileEntity.reportTaskId}"
            )

            //then
            assertThat(result).isEqualTo(DownloadFile("Report-File", reportFileBytes))
            coVerify {
                reportFileRepository.retrieveReportFileByTenantAndReference(
                    "my-tenant",
                    reportFileEntity.reportTaskId,
                    "my-user"
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
                    tenant = "my-tenant",
                    username = "my-user",
                    taskReference = "-1"
                )
            }

            //then
            assertThat(caught).all {
                prop(IllegalArgumentException::message).isEqualTo("File not found")
            }
            coVerify {
                reportFileRepository.retrieveReportFileByTenantAndReference(
                    "my-tenant",
                    -1,
                    "my-user"
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

            // when
            val caught = assertThrows<IllegalArgumentException> {
                reportServiceImpl.read(
                    tenant = "my-tenant",
                    username = "my-user",
                    taskReference = "unknown ref"
                )
            }

            //then
            assertThat(caught).all {
                prop(IllegalArgumentException::message).isEqualTo("Requested file not found")
            }
            coVerify {
                reportTaskRepository.findByTenantReferenceAndReference(
                    "my-tenant",
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
            coEvery {
                reportTaskRepository.findByTenantReferenceAndReference(
                    any(),
                    any()
                )
            } returns reportTaskEntity

            // when
            val caught = assertThrows<ExitStatusException> {
                reportServiceImpl.read(
                    tenant = "my-tenant",
                    username = "my-user",
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
                    "my-tenant",
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
            coEvery {
                reportTaskRepository.findByTenantReferenceAndReference(
                    any(),
                    any()
                )
            } returns reportTaskEntity

            // when
            val caught = assertThrows<ReportGenerationException> {
                reportServiceImpl.read(
                    tenant = "my-tenant",
                    username = "my-user",
                    taskReference = reportTaskEntity.reference
                )
            }

            //then
            assertThat(caught).all {
                prop(ReportGenerationException::message).isEqualTo("There was an error generating the file: null")
            }
            coVerify {
                reportTaskRepository.findByTenantReferenceAndReference(
                    "my-tenant",
                    reportTaskEntity.reference,
                )
            }
            confirmVerified(reportTaskRepository)
        }

    @Test
    internal fun `should handle deletion of a list of report`() = testDispatcherProvider.runTest {
        // given
        val reportServiceImpl = buildReportService()
        coEvery {
            reportRepository.getUpdatableReportReferences(
                tenant = refEq("my-tenant"),
                references = eq(setOf("report-ref", "report-ref2")),
                creatorId = 456L
            )
        } returns listOf("report-ref", "report-ref2")
        coEvery { reportRepository.deleteAllByReference(any()) } returns 1
        coEvery { userProvider.findIdByUsername("the-user") } returns 456L

        // when
        reportServiceImpl.delete(
            tenant = "my-tenant",
            username = "the-user",
            references = setOf("report-ref", "report-ref2")
        )

        // then
        coVerifyOrder {
            userProvider.findIdByUsername("the-user")
            reportRepository.getUpdatableReportReferences(
                tenant = refEq("my-tenant"),
                references = eq(setOf("report-ref", "report-ref2")),
                creatorId = 456L
            )
            reportRepository.deleteAllByReference(listOf("report-ref", "report-ref2"))
        }
        confirmVerified(
            reportRepository,
            tenantProvider,
            userProvider,
            campaignRepository,
            reportDataComponentRepository,
            dataSeriesRepository,
            idGenerator,
            reportConverter
        )
    }

    @Test
    internal fun `should not delete the report when one item in the list of retrieved report is not owned or shared`() =
        testDispatcherProvider.runTest {
            // given
            val reportServiceImpl = buildReportService()
            coEvery {
                reportRepository.getUpdatableReportReferences(
                    tenant = refEq("my-tenant"),
                    references = eq(setOf("report-ref", "report-ref-3")),
                    creatorId = 457L
                )
            } returns listOf("report-ref", "report-ref-4")
            coEvery { userProvider.findIdByUsername("other-user") } returns 457L

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.delete(
                    tenant = "my-tenant",
                    username = "other-user",
                    references = setOf("report-ref", "report-ref-3")
                )
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_DELETE_DENY)

            coVerifyOrder {
                userProvider.findIdByUsername("other-user")
                reportRepository.getUpdatableReportReferences(
                    tenant = refEq("my-tenant"),
                    references = eq(setOf("report-ref", "report-ref-3")),
                    creatorId = 457L
                )
            }
            confirmVerified(
                reportRepository,
                tenantProvider,
                userProvider,
                campaignRepository,
                reportDataComponentRepository,
                dataSeriesRepository,
                idGenerator,
                reportConverter
            )
        }


    private fun CoroutineScope.buildReportService() = ReportServiceImpl(
        reportRepository,
        tenantProvider,
        userProvider,
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