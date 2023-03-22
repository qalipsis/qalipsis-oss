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

package io.qalipsis.core.head.model.converter

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ReportDataComponentRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataTable
import io.qalipsis.core.head.model.Diagram
import io.qalipsis.core.head.model.Report
import io.qalipsis.core.head.report.SharingMode
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

/**
 * @author Joël Valère
 */
@WithMockk
internal class ReportConverterImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    @MockK
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

    @MockK
    private lateinit var reportDataComponentRepository: ReportDataComponentRepository

    @InjectMockKs
    private lateinit var reportConverterImpl: ReportConverterImpl

    @Test
    fun `should convert simple entity to model with all empty lists`() = testDispatcherProvider.runTest {
        //given
        val version = Instant.now().minusMillis(1)
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        val reportEntity = ReportEntity(
            id = 1L,
            reference = "report-ref",
            version = version,
            tenantId = 123L,
            creatorId = 456L,
            displayName = "my-report-name",
            sharingMode = SharingMode.NONE,
            query = "This is the query",
            campaignKeys = listOf(),
            campaignNamesPatterns = listOf(),
            scenarioNamesPatterns = listOf(),
            dataComponents = listOf()
        )
        //when
        val report = reportConverterImpl.convertToModel(reportEntity)

        //then
        assertThat(report).isDataClassEqualTo(
            Report(
                reference = "report-ref",
                version = version,
                creator = "the-user",
                displayName = "my-report-name",
                sharingMode = SharingMode.NONE,
                campaignKeys = listOf(),
                campaignNamesPatterns = listOf(),
                resolvedCampaignKeys = listOf(),
                scenarioNamesPatterns = listOf(),
                resolvedScenarioNames = listOf(),
                dataComponents = listOf()
            )
        )
        coVerifyOrder {
            userRepository.findUsernameById(456L)
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository, reportDataComponentRepository)
    }

    @Test
    fun `should convert complete entity to model`() = testDispatcherProvider.runTest {
        //given
        val dataSeries1 = relaxedMockk<DataSeries>()
        val dataSeries2 = relaxedMockk<DataSeries>()
        val dataSeries3 = relaxedMockk<DataSeries>()
        val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries1
        }
        val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries2
        }
        val dataSeriesEntity3 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries3
        }
        val version = Instant.now().minusMillis(1)
        coEvery { reportDataComponentRepository.findByIdInOrderById(listOf(1L, 2L)) } returns listOf(
            ReportDataComponentEntity(
                id = 1L,
                type = DataComponentType.DIAGRAM,
                reportId = 1L,
                dataSeries = listOf(dataSeriesEntity1, dataSeriesEntity2)
            ),
            ReportDataComponentEntity(
                id = 2L,
                type = DataComponentType.DATA_TABLE,
                reportId = 1L,
                dataSeries = listOf(dataSeriesEntity3)
            )
        )
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        coEvery {
            campaignRepository.findKeysByTenantIdAndNamePatterns(123L, listOf("camp-1%", "%camp-2"))
        } returns listOf(
            "campaign-key1",
            "campaign-key2",
            "campaign-key3"
        )
        coEvery {
            campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                123L,
                listOf("scen-1%", "%scen-2"),
                listOf(
                    "camp-1",
                    "camp-2",
                    "campaign-key1",
                    "campaign-key2",
                    "campaign-key3"
                )
            )
        } returns listOf("scenario-1", "scenario-2", "scenario-3")
        val reportEntity = ReportEntity(
            id = 1L,
            reference = "report-ref",
            version = version,
            tenantId = 123L,
            creatorId = 456L,
            displayName = "my-report-name",
            sharingMode = SharingMode.WRITE,
            query = "This is the query",
            campaignKeys = listOf("camp-1", "camp-2"),
            campaignNamesPatterns = listOf("camp-1*", "*camp-2"),
            scenarioNamesPatterns = listOf("scen-1*", "*scen-2"),
            dataComponents = listOf(
                ReportDataComponentEntity(
                    id = 1L,
                    type = DataComponentType.DIAGRAM,
                    reportId = 1L,
                    dataSeries = listOf()
                ),
                ReportDataComponentEntity(
                    id = 2L,
                    type = DataComponentType.DATA_TABLE,
                    reportId = 1L,
                    dataSeries = listOf()
                )
            )
        )
        //when
        val report = reportConverterImpl.convertToModel(reportEntity)

        //then
        assertThat(report).isDataClassEqualTo(
            Report(
                reference = "report-ref",
                version = version,
                creator = "the-user",
                displayName = "my-report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("camp-1", "camp-2"),
                campaignNamesPatterns = listOf("camp-1*", "*camp-2"),
                resolvedCampaignKeys = listOf("campaign-key1", "campaign-key2", "campaign-key3"),
                scenarioNamesPatterns = listOf("scen-1*", "*scen-2"),
                resolvedScenarioNames = listOf("scenario-1", "scenario-2", "scenario-3"),
                dataComponents = listOf(
                    Diagram(datas = listOf(dataSeries1, dataSeries2)),
                    DataTable(datas = listOf(dataSeries3))
                )
            )
        )
        coVerifyOrder {
            reportDataComponentRepository.findByIdInOrderById(listOf(1L, 2L))
            campaignRepository.findKeysByTenantIdAndNamePatterns(123L, listOf("camp-1%", "%camp-2"))
            campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                123L,
                listOf("scen-1%", "%scen-2"),
                listOf(
                    "camp-1",
                    "camp-2",
                    "campaign-key1",
                    "campaign-key2",
                    "campaign-key3"
                )
            )
            userRepository.findUsernameById(456L)
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository, reportDataComponentRepository)
    }

    @Test
    fun `should convert entity to model with empty data components`() = testDispatcherProvider.runTest {
        //given
        val version = Instant.now().minusMillis(1)
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        coEvery {
            campaignRepository.findKeysByTenantIdAndNamePatterns(123L, listOf("camp-1%", "%camp-2"))
        } returns listOf(
            "campaign-key1",
            "campaign-key2",
            "campaign-key3"
        )
        coEvery {
            campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                123L,
                listOf("scen-1%", "%scen-2"),
                listOf(
                    "camp-1",
                    "camp-2",
                    "campaign-key1",
                    "campaign-key2",
                    "campaign-key3"
                )
            )
        } returns listOf("scenario-1", "scenario-2", "scenario-3")
        val reportEntity = ReportEntity(
            id = 1L,
            reference = "report-ref",
            version = version,
            tenantId = 123L,
            creatorId = 456L,
            displayName = "my-report-name",
            sharingMode = SharingMode.READONLY,
            query = "This is the query",
            campaignKeys = listOf("camp-1", "camp-2"),
            campaignNamesPatterns = listOf("camp-1*", "*camp-2"),
            scenarioNamesPatterns = listOf("scen-1*", "*scen-2"),
            dataComponents = listOf()
        )
        //when
        val report = reportConverterImpl.convertToModel(reportEntity)

        //then
        assertThat(report).isDataClassEqualTo(
            Report(
                reference = "report-ref",
                version = version,
                creator = "the-user",
                displayName = "my-report-name",
                sharingMode = SharingMode.READONLY,
                campaignKeys = listOf("camp-1", "camp-2"),
                campaignNamesPatterns = listOf("camp-1*", "*camp-2"),
                resolvedCampaignKeys = listOf("campaign-key1", "campaign-key2", "campaign-key3"),
                scenarioNamesPatterns = listOf("scen-1*", "*scen-2"),
                resolvedScenarioNames = listOf("scenario-1", "scenario-2", "scenario-3"),
                dataComponents = listOf()
            )
        )
        coVerifyOrder {
            campaignRepository.findKeysByTenantIdAndNamePatterns(123L, listOf("camp-1%", "%camp-2"))
            campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                123L,
                listOf("scen-1%", "%scen-2"),
                listOf(
                    "camp-1",
                    "camp-2",
                    "campaign-key1",
                    "campaign-key2",
                    "campaign-key3"
                )
            )
            userRepository.findUsernameById(456L)
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository, reportDataComponentRepository)
    }

    @Test
    fun `should convert entity to model when campaign names patterns list is empty`() = testDispatcherProvider.runTest {
        //given
        val dataSeries1 = relaxedMockk<DataSeries>()
        val dataSeries2 = relaxedMockk<DataSeries>()
        val dataSeries3 = relaxedMockk<DataSeries>()
        val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries1
        }
        val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries2
        }
        val dataSeriesEntity3 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries3
        }
        val version = Instant.now().minusMillis(1)
        coEvery { reportDataComponentRepository.findByIdInOrderById(listOf(1L, 2L)) } returns listOf(
            ReportDataComponentEntity(
                id = 1L,
                type = DataComponentType.DIAGRAM,
                reportId = 1L,
                dataSeries = listOf(dataSeriesEntity1, dataSeriesEntity2)
            ),
            ReportDataComponentEntity(
                id = 2L,
                type = DataComponentType.DATA_TABLE,
                reportId = 1L,
                dataSeries = listOf(dataSeriesEntity3)
            )
        )
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        coEvery {
            campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                123L,
                listOf("scen-1%", "%scen-2"),
                listOf(
                    "camp-1",
                    "camp-2"
                )
            )
        } returns listOf("scenario-1", "scenario-2", "scenario-3")
        val reportEntity = ReportEntity(
            id = 1L,
            reference = "report-ref",
            version = version,
            tenantId = 123L,
            creatorId = 456L,
            displayName = "my-report-name",
            sharingMode = SharingMode.WRITE,
            query = "This is the query",
            campaignKeys = listOf("camp-1", "camp-2"),
            campaignNamesPatterns = listOf(),
            scenarioNamesPatterns = listOf("scen-1*", "*scen-2"),
            dataComponents = listOf(
                ReportDataComponentEntity(
                    id = 1L,
                    type = DataComponentType.DIAGRAM,
                    reportId = 1L,
                    dataSeries = listOf()
                ),
                ReportDataComponentEntity(
                    id = 2L,
                    type = DataComponentType.DATA_TABLE,
                    reportId = 1L,
                    dataSeries = listOf()
                )
            )
        )
        //when
        val report = reportConverterImpl.convertToModel(reportEntity)

        //then
        assertThat(report).isDataClassEqualTo(
            Report(
                reference = "report-ref",
                version = version,
                creator = "the-user",
                displayName = "my-report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("camp-1", "camp-2"),
                campaignNamesPatterns = listOf(),
                resolvedCampaignKeys = listOf(),
                scenarioNamesPatterns = listOf("scen-1*", "*scen-2"),
                resolvedScenarioNames = listOf("scenario-1", "scenario-2", "scenario-3"),
                dataComponents = listOf(
                    Diagram(datas = listOf(dataSeries1, dataSeries2)),
                    DataTable(datas = listOf(dataSeries3))
                )
            )
        )
        coVerifyOrder {
            reportDataComponentRepository.findByIdInOrderById(listOf(1L, 2L))
            campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                123L,
                listOf("scen-1%", "%scen-2"),
                listOf(
                    "camp-1",
                    "camp-2"
                )
            )
            userRepository.findUsernameById(456L)
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository, reportDataComponentRepository)
    }

    @Test
    fun `should convert entity to model when scenario names patterns list is empty`() = testDispatcherProvider.runTest {
        //given
        val dataSeries1 = relaxedMockk<DataSeries>()
        val dataSeries2 = relaxedMockk<DataSeries>()
        val dataSeries3 = relaxedMockk<DataSeries>()
        val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries1
        }
        val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries2
        }
        val dataSeriesEntity3 = relaxedMockk<DataSeriesEntity> {
            every { toModel(any()) } returns dataSeries3
        }
        val version = Instant.now().minusMillis(1)
        coEvery { reportDataComponentRepository.findByIdInOrderById(listOf(1L, 2L)) } returns listOf(
            ReportDataComponentEntity(
                id = 1L,
                type = DataComponentType.DIAGRAM,
                reportId = 1L,
                dataSeries = listOf(dataSeriesEntity1, dataSeriesEntity2)
            ),
            ReportDataComponentEntity(
                id = 2L,
                type = DataComponentType.DATA_TABLE,
                reportId = 1L,
                dataSeries = listOf(dataSeriesEntity3)
            )
        )
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        coEvery {
            campaignRepository.findKeysByTenantIdAndNamePatterns(123L, listOf("camp-1%", "%camp-2"))
        } returns listOf(
            "campaign-key1",
            "campaign-key2",
            "campaign-key3"
        )
        coEvery {
            campaignScenarioRepository.findNameByCampaignKeys(
                123L,
                listOf(
                    "camp-1",
                    "camp-2",
                    "campaign-key1",
                    "campaign-key2",
                    "campaign-key3"
                )
            )
        } returns listOf(
            "scenario-1",
            "scenario-2",
            "scenario-3",
            "scenario-4"
        )
        val reportEntity = ReportEntity(
            id = 1L,
            reference = "report-ref",
            version = version,
            tenantId = 123L,
            creatorId = 456L,
            displayName = "my-report-name",
            sharingMode = SharingMode.WRITE,
            query = "This is the query",
            campaignKeys = listOf("camp-1", "camp-2"),
            campaignNamesPatterns = listOf("camp-1*", "*camp-2"),
            scenarioNamesPatterns = listOf(),
            dataComponents = listOf(
                ReportDataComponentEntity(
                    id = 1L,
                    type = DataComponentType.DIAGRAM,
                    reportId = 1L,
                    dataSeries = listOf()
                ),
                ReportDataComponentEntity(
                    id = 2L,
                    type = DataComponentType.DATA_TABLE,
                    reportId = 1L,
                    dataSeries = listOf()
                )
            )
        )
        //when
        val report = reportConverterImpl.convertToModel(reportEntity)

        //then
        assertThat(report).isDataClassEqualTo(
            Report(
                reference = "report-ref",
                version = version,
                creator = "the-user",
                displayName = "my-report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("camp-1", "camp-2"),
                campaignNamesPatterns = listOf("camp-1*", "*camp-2"),
                resolvedCampaignKeys = listOf("campaign-key1", "campaign-key2", "campaign-key3"),
                scenarioNamesPatterns = listOf(),
                resolvedScenarioNames = listOf("scenario-1", "scenario-2", "scenario-3", "scenario-4"),
                dataComponents = listOf(
                    Diagram(datas = listOf(dataSeries1, dataSeries2)),
                    DataTable(datas = listOf(dataSeries3))
                )
            )
        )
        coVerifyOrder {
            reportDataComponentRepository.findByIdInOrderById(listOf(1L, 2L))
            campaignRepository.findKeysByTenantIdAndNamePatterns(123L, listOf("camp-1%", "%camp-2"))
            campaignScenarioRepository.findNameByCampaignKeys(
                123L,
                refEq(
                    listOf(
                        "camp-1",
                        "camp-2",
                        "campaign-key1",
                        "campaign-key2",
                        "campaign-key3"
                    ), true
                )
            )
            userRepository.findUsernameById(456L)
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository, reportDataComponentRepository)
    }
}