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
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportFileEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.ReportFileRepository
import io.qalipsis.core.head.jdbc.repository.ReportRepository
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.model.ReportTaskStatus
import io.qalipsis.core.head.report.thymeleaf.ThymeleafReportServiceImpl
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit

@WithMockk
internal class ReportGeneratorTests {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var reportRepository: ReportRepository

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    @MockK
    private lateinit var reportTaskRepository: ReportTaskRepository

    @MockK
    private lateinit var reportFileRepository: ReportFileRepository

    @MockK
    private lateinit var idGenerator: IdGenerator

    @MockK
    private lateinit var mockTemplateReportService: ThymeleafReportServiceImpl

    @MockK
    private lateinit var mockReportFileBuilder: ReportFileBuilder

    @InjectMockKs
    private lateinit var reportGenerator: ReportGenerator

    private val html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <title></title>
                </head>
                <body>
                  <h1>Latest Meter Report</h1>
                </body>
                </html>
            """.trimIndent()
    private val byteArray = html.toByteArray()

    private val dataSeries = listOf(
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
        ), DataSeriesEntity(
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

    private val reportEntity = ReportEntity(
        tenantId = 42L,
        displayName = "current-report",
        reference = "qoi78wizened",
        creatorId = 4L,
        campaignKeys = listOf("key1", "key2"),
        campaignNamesPatterns = emptyList(),
        scenarioNamesPatterns = emptyList(),
        dataComponents = listOf(
            ReportDataComponentEntity(id = 1, type = DataComponentType.DIAGRAM, -1, listOf(dataSeries[0])),
            ReportDataComponentEntity(id = 2, type = DataComponentType.DATA_TABLE, -1, listOf(dataSeries[1]))
        )
    )
    private val now: Instant = Instant.parse("2023-02-22T00:00:00.00Z")
    private val reportTaskEntity = ReportTaskEntity(
        creator = "user",
        creationTimestamp = now,
        id = 11,
        reportId = reportEntity.id,
        reference = "report-task-1",
        tenantReference = "my-tenant",
        status = ReportTaskStatus.PENDING,
        updateTimestamp = now
    )

    private val reportFileEntity = ReportFileEntity(
        name = "${reportEntity.displayName} ${now.truncatedTo(ChronoUnit.SECONDS)}",
        creationTimestamp = now,
        id = -1,
        reportTaskId = reportTaskEntity.id,
        fileContent = html.toByteArray()
    )


    @Test
    fun `should update the status of the report task to completed when report file generation is successful`() =
        testDispatcherProvider.runTest {
            //given
            val reportTaskEntity2 =
                reportTaskEntity.copy(status = ReportTaskStatus.COMPLETED, updateTimestamp = now.plusSeconds(28))
            val campaignData = CampaignData(
                name = "campaign-1",
                result = ExecutionStatus.ABORTED,
                startedMinions = 6,
                completedMinions = 5,
                successfulExecutions = 5,
                failedExecutions = 1,
                zones = setOf("GER", "DM"),
                executionTime = 15
            )
            val campaignData2 = campaignData.copy(
                name = "campaign-2",
                result = ExecutionStatus.SUCCESSFUL,
                startedMinions = 46,
                completedMinions = 46,
                successfulExecutions = 25,
                failedExecutions = 21,
            )
            coEvery { reportTaskRepository.update(any()) } returns reportTaskEntity2
            coEvery { reportRepository.findByTenantAndReference(any(), any()) } returns reportEntity
            coEvery { idGenerator.short() } returns reportTaskEntity.reference
            coEvery {
                campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns listOf(campaignData, campaignData2)
            coEvery {
                mockTemplateReportService.generatePdf(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns byteArray
            val campaignReportDetail = relaxedMockk<CampaignReportDetail>()
            coEvery { mockReportFileBuilder.execute(any(), any(), any()) } returns campaignReportDetail
            coEvery { reportFileRepository.save(any<ReportFileEntity>()) } returns reportFileEntity
            mockkStatic(Files::class)
            val tempPath = File("temp/${reportEntity.displayName}-${reportTaskEntity.reference}").toPath()
            coEvery { Files.createTempDirectory(any()).toAbsolutePath() } returns tempPath

            //when
            reportGenerator.processTaskGeneration("my-tenant", "user", "qoi78wizened", reportEntity, reportTaskEntity)

            //then
            coVerifyOrder {
                campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                    42L,
                    listOf("key1", "key2"),
                    emptyList(),
                    emptyList()
                )
                mockReportFileBuilder.execute(reportEntity, "my-tenant", listOf(campaignData, campaignData2))
                mockTemplateReportService.generatePdf(
                    reportEntity,
                    campaignReportDetail,
                    reportTaskEntity,
                    "user",
                    dataSeries,
                    "my-tenant",
                    tempPath
                )
                reportFileRepository.save(withArg {
                    assertThat(it).all {
                        prop(ReportFileEntity::id).isEqualTo(-1)
                        prop(ReportFileEntity::reportTaskId).isEqualTo(11)
                        prop(ReportFileEntity::name).isNotNull()
                        prop(ReportFileEntity::fileContent).isEqualTo(byteArray)
                        prop(ReportFileEntity::creationTimestamp).isNotNull()
                    }
                })
                reportTaskRepository.update(withArg {
                    assertThat(it).all {
                        prop(ReportTaskEntity::id).isEqualTo(11)
                        prop(ReportTaskEntity::reportId).isEqualTo(reportEntity.id)
                        prop(ReportTaskEntity::reference).isEqualTo("report-task-1")
                        prop(ReportTaskEntity::tenantReference).isEqualTo("my-tenant")
                        prop(ReportTaskEntity::status).isEqualTo(ReportTaskStatus.COMPLETED)
                        prop(ReportTaskEntity::creationTimestamp).isNotNull()
                        prop(ReportTaskEntity::updateTimestamp).isNotNull()
                        prop(ReportTaskEntity::failureReason).isNull()
                        prop(ReportTaskEntity::creator).isEqualTo("user")
                    }
                })
            }
            confirmVerified(
                campaignRepository,
                mockReportFileBuilder,
                mockTemplateReportService,
                reportFileRepository,
                reportTaskRepository
            )
        }

    @Test
    fun `should update the status of the report task to failed and throw an exception when there is a failure`() =
        testDispatcherProvider.runTest {
            //given
            val reportTaskEntity2 =
                reportTaskEntity.copy(status = ReportTaskStatus.PROCESSING, updateTimestamp = now.plusSeconds(28))
            val reportTaskEntity3 =
                reportTaskEntity.copy(status = ReportTaskStatus.FAILED, updateTimestamp = now.plusSeconds(52))
            coEvery { reportTaskRepository.update(any()) } returns reportTaskEntity2 andThen reportTaskEntity3
            coEvery { reportRepository.findByTenantAndReference(any(), any()) } returns reportEntity
            coEvery { idGenerator.short() } returns reportTaskEntity.reference
            coEvery {
                campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns listOf()

            //when
            val caught: IllegalArgumentException = assertThrows<IllegalArgumentException> {
                reportGenerator.processTaskGeneration(
                    "my-tenant",
                    "user",
                    "qoi78wizened",
                    reportEntity,
                    reportTaskEntity
                )
            }

            //then
            assertThat(caught).all {
                prop(IllegalArgumentException::message).isEqualTo("Encountered an error while generating report file : No matching campaign for specified campaign keys, campaign name patterns and scenario name patterns")
            }
            coVerifyOrder {
                campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                    42L,
                    listOf("key1", "key2"),
                    emptyList(),
                    emptyList()
                )
                reportTaskRepository.update(withArg {
                    assertThat(it).all {
                        prop(ReportTaskEntity::id).isEqualTo(11)
                        prop(ReportTaskEntity::reportId).isEqualTo(reportEntity.id)
                        prop(ReportTaskEntity::reference).isEqualTo("report-task-1")
                        prop(ReportTaskEntity::tenantReference).isEqualTo("my-tenant")
                        prop(ReportTaskEntity::status).isEqualTo(ReportTaskStatus.FAILED)
                        prop(ReportTaskEntity::creationTimestamp).isNotNull()
                        prop(ReportTaskEntity::updateTimestamp).isNotNull()
                        prop(ReportTaskEntity::failureReason).isEqualTo("No matching campaign for specified campaign keys, campaign name patterns and scenario name patterns")
                        prop(ReportTaskEntity::creator).isEqualTo("user")
                    }
                })
            }
            confirmVerified(
                campaignRepository,
                reportRepository,
                reportTaskRepository
            )
        }

}