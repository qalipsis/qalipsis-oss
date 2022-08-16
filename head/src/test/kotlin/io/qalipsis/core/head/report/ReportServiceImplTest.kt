package io.qalipsis.core.head.report

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ReportDataComponentRepository
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.jdbc.repository.ReportRepository
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
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_CAMPAIGN_KEYS_NOT_ALLOWED
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_DATA_SERIES_NOT_ALLOWED
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_DELETE_DENY
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_FETCH_DENY
import io.qalipsis.core.head.report.ReportServiceImpl.Companion.REPORT_UPDATE_DENY
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

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
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

    @MockK
    private lateinit var dataSeriesRepository: DataSeriesRepository

    @MockK
    private lateinit var reportDataComponentRepository: ReportDataComponentRepository

    @MockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var reportServiceImpl: ReportServiceImpl

    private val dataSeries = listOf(
        DataSeriesEntity(
            reference = "series-ref-1",
            tenantId = 123L,
            creatorId = 456L,
            displayName = "series-name-1",
            dataType = DataType.EVENTS
        ),
        DataSeriesEntity(
            reference = "series-ref-2",
            tenantId = 123L,
            creatorId = 456L,
            displayName = "series-name-2",
            dataType = DataType.METERS
        )
    )

    @Test
    internal fun `should create the report with the default sharing mode and empty campaign keys and scenario names patterns`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { reportRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference("the-tenant") } returns 123L
            coEvery { userRepository.findIdByUsername("the-user") } returns 456L
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("the-tenant", emptyList()) } returns emptySet()
            coEvery { campaignScenarioRepository.findNameByCampaignKeys(any()) } returns emptyList()
            coEvery { idGenerator.short() } returns "the-reference"

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(displayName = "report-name")

            // when
            val result = reportServiceImpl.create(tenant = "the-tenant", creator = "the-user", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)

            // then
            assertThat(result).all {
                prop(Report::reference).isEqualTo("the-reference")
                prop(Report::creator).isEqualTo("the-user")
                prop(Report::displayName).isEqualTo("report-name")
                prop(Report::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(Report::campaignKeys).isEmpty()
                prop(Report::campaignNamesPatterns).isEmpty()
                prop(Report::resolvedCampaignKeys).isEmpty()
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
                reportRepository.save(any())
                campaignScenarioRepository.findNameByCampaignKeys(any())
            }
            coVerifyNever {
                reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
            }
        }

    @Test
    internal fun `should create the report by specifying all fields`() = testDispatcherProvider.runTest {
        // given
        coEvery { reportRepository.save(any()) } returnsArgument 0
        coEvery { tenantRepository.findIdByReference("the-tenant") } returns 123L
        coEvery { userRepository.findIdByUsername("the-user") } returns 456L
        coEvery { campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1", "campaign-key2")) } returns setOf("campaign-key1", "campaign-key2")
        coEvery { campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any()) } returns listOf("campaign-key1", "campaign-key2", "campaign-key3")
        coEvery { campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(any(), any()) } returns listOf("scenario-1", "scenario-2", "scenario-3")
        coEvery { dataSeriesRepository.checkExistenceByTenantAndReference("the-tenant", any()) } returns true
        coEvery { dataSeriesRepository.findByReferenceAndTenant("series-ref-1", "the-tenant") } returns dataSeries[0]
        coEvery { dataSeriesRepository.findByReferenceAndTenant("series-ref-2", "the-tenant") } returns dataSeries[1]
        coEvery { reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>()) } returns flowOf(
            ReportDataComponentEntity(id = 1, reportId = -1, type = DataComponentType.DIAGRAM, dataSeries = listOf(dataSeries[0])),
            ReportDataComponentEntity(id = 2, reportId = -1, type = DataComponentType.DATA_TABLE, dataSeries = listOf(dataSeries[1]))
        )
        coEvery { idGenerator.short() } returns "report-ref"
        coEvery { userRepository.findUsernameById(any()) } returns "the-user"

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
        val result = reportServiceImpl.create(tenant = "the-tenant", creator = "the-user", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)

        // then
        assertThat(result).all {
            prop(Report::reference).isEqualTo("report-ref")
            prop(Report::creator).isEqualTo("the-user")
            prop(Report::displayName).isEqualTo("report-name")
            prop(Report::sharingMode).isEqualTo(SharingMode.NONE)
            prop(Report::campaignKeys).hasSize(2)
            prop(Report::campaignNamesPatterns).hasSize(2)
            prop(Report::resolvedCampaignKeys).hasSize(3)
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
            campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1", "campaign-key2"))
            dataSeriesRepository.checkExistenceByTenantAndReference(tenant = "the-tenant", reference = any())
            tenantRepository.findIdByReference("the-tenant")
            userRepository.findIdByUsername("the-user")
            reportRepository.save(any())
            reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
            campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any())
            campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(any(), any())
            userRepository.findUsernameById(any())
        }
    }

    @Test
    internal fun `should not create the report with a list of campaign keys that do not belong to the tenant`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1")) } returns emptySet()

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(displayName = "report-name", campaignKeys = listOf("campaign-key1"))

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.create(tenant = "the-tenant", creator = "the-user", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_CAMPAIGN_KEYS_NOT_ALLOWED)
        }

    @Test
    internal fun `should not create the report when data series do not belong to the tenant`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("the-tenant", emptyList()) } returns emptySet()
            coEvery { dataSeriesRepository.checkExistenceByTenantAndReference("the-tenant", any()) } returns false

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
                reportServiceImpl.create(tenant = "the-tenant", creator = "the-user", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_DATA_SERIES_NOT_ALLOWED)
        }

    @Test
    internal fun `should get the report if shared`() = testDispatcherProvider.runTest {
        // given
        coEvery { reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "the-tenant", reference = "report-ref", creatorId = 456L) } returns ReportEntity(
            reference = "report-ref",
            tenantId = -1,
            creatorId = 123L,
            displayName = "report-name",
            sharingMode = SharingMode.READONLY,
            campaignNamesPatterns = listOf("*"),
        )
        coEvery { userRepository.findIdByUsername("other-user") } returns 456L
        coEvery { userRepository.findUsernameById(123L) } returns "the-user"
        coEvery { campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any()) } returns listOf("campaign-key1")
        coEvery { campaignScenarioRepository.findNameByCampaignKeys(any()) } returns listOf("scenario-1", "scenario-2")

        // when
        val result = reportServiceImpl.get(tenant = "the-tenant", username = "other-user", reference = "report-ref")

        // then
        assertThat(result).all {
            prop(Report::reference).isEqualTo("report-ref")
            prop(Report::creator).isEqualTo("the-user")
            prop(Report::displayName).isEqualTo("report-name")
            prop(Report::sharingMode).isEqualTo(SharingMode.READONLY)
            prop(Report::campaignKeys).hasSize(0)
            prop(Report::campaignNamesPatterns).hasSize(1)
            prop(Report::resolvedCampaignKeys).hasSize(1)
            prop(Report::scenarioNamesPatterns).hasSize(0)
            prop(Report::resolvedScenarioNames).hasSize(2)
            prop(Report::dataComponents).hasSize(0)
        }

        coVerifyNever {
            reportDataComponentRepository.findByIdInOrderById(any())
        }

        coVerifyOrder {
            userRepository.findIdByUsername("other-user")
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "the-tenant", reference = "report-ref", creatorId = 456L)
            userRepository.findUsernameById(123L)
            campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any())
            campaignScenarioRepository.findNameByCampaignKeys(any())
        }
    }

    @Test
    internal fun `should get the report if not shared but owned`() = testDispatcherProvider.runTest {
        // given
        coEvery { reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "the-tenant", reference = "report-ref", creatorId = 456L) } returns ReportEntity(
            reference = "report-ref",
            tenantId = -1,
            creatorId = 456L,
            displayName = "report-name",
            sharingMode = SharingMode.NONE,
            campaignNamesPatterns = listOf("*"),
        )
        coEvery { userRepository.findIdByUsername("the-user") } returns 456L
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        coEvery { campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any()) } returns listOf("campaign-key1")
        coEvery { campaignScenarioRepository.findNameByCampaignKeys(any()) } returns listOf("scenario-1", "scenario-2")

        // when
        val result = reportServiceImpl.get(tenant = "the-tenant", username = "the-user", reference = "report-ref")

        // then
        assertThat(result).all {
            prop(Report::reference).isEqualTo("report-ref")
            prop(Report::creator).isEqualTo("the-user")
            prop(Report::displayName).isEqualTo("report-name")
            prop(Report::sharingMode).isEqualTo(SharingMode.NONE)
            prop(Report::campaignKeys).hasSize(0)
            prop(Report::campaignNamesPatterns).hasSize(1)
            prop(Report::resolvedCampaignKeys).hasSize(1)
            prop(Report::scenarioNamesPatterns).hasSize(0)
            prop(Report::resolvedScenarioNames).hasSize(2)
            prop(Report::dataComponents).hasSize(0)
        }

        coVerifyNever {
            reportDataComponentRepository.findByIdInOrderById(any())
        }

        coVerifyOrder {
            userRepository.findIdByUsername("the-user")
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "the-tenant", reference = "report-ref", creatorId = 456L)
            userRepository.findUsernameById(456L)
            campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any())
            campaignScenarioRepository.findNameByCampaignKeys(any())
        }
    }

    @Test
    internal fun `should not get the report if not shared and not owned`() = testDispatcherProvider.runTest {
        // given
        val creatorId = 456L
        coEvery { reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "the-tenant", reference = "report-ref", creatorId = creatorId) } returns null
        coEvery { userRepository.findIdByUsername("the-user") } returns creatorId

        // when
        val exception = assertThrows<java.lang.IllegalArgumentException> {
            reportServiceImpl.get(tenant = "the-tenant", username = "the-user", reference = "report-ref")
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_FETCH_DENY)
    }

    @Test
    internal fun `should not get the report if the user do not belongs to the tenant and OR or the report reference is not found`() =
        testDispatcherProvider.runTest {
            // given
            val creatorId = 456L
            coEvery { reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "the-tenant", reference = "report-ref", creatorId = creatorId) } returns null
            coEvery { userRepository.findIdByUsername("the-user") } returns creatorId

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.get(tenant = "the-tenant", username = "the-user", reference = "report-ref")
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_FETCH_DENY)
        }

    @Test
    internal fun `should update the report when shared in write mode and save if there are changes`() =
        testDispatcherProvider.runTest {
            // given
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
            coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L) } returns reportEntity
            coEvery { userRepository.findUsernameById(456L) } returns "the-user"
            coEvery { userRepository.findIdByUsername("the-user") } returns 456L
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1")) } returns setOf("campaign-key1")
            coEvery { campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any()) } returns listOf("campaign-key1", "campaign-key2", "campaign-key3")
            coEvery { campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(any(), any()) } returns listOf("scenario-1", "scenario-2", "scenario-3")
            coEvery { dataSeriesRepository.checkExistenceByTenantAndReference("the-tenant", any()) } returns true
            coEvery { dataSeriesRepository.findByReferenceAndTenant("series-ref-1", "the-tenant") } returns dataSeries[0]
            coEvery { reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>()) } returns flowOf(
                ReportDataComponentEntity(id = 1, reportId = -1, type = DataComponentType.DATA_TABLE, dataSeries = listOf(dataSeries[0]))
            )
            coEvery { reportDataComponentRepository.deleteByReportId(any()) } returns 0
            coEvery { reportRepository.update(any()) } returnsArgument 0

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
                displayName = "new-report-name",
                sharingMode = SharingMode.NONE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*", "\\w"),
                scenarioNamesPatterns = listOf("\\w"),
                dataComponents = listOf(DataTableCreationAndUpdateRequest(dataSeriesReferences = listOf("series-ref-1")))
            )

            // when
            val result = reportServiceImpl.update(tenant = "the-tenant", username = "the-user", reference =  "report-ref", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)

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
                prop(Report::resolvedCampaignKeys).all {
                    hasSize(3)
                    containsOnly("campaign-key1", "campaign-key2", "campaign-key3")
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
                userRepository.findIdByUsername("the-user")
                reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L)
                userRepository.findUsernameById(456L)
                campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1"))
                reportDataComponentRepository.deleteByReportId(any())
                reportRepository.update(any())
                reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
                campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any())
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(any(), any())
                userRepository.findUsernameById(456L)
            }
        }

    @Test
    internal fun `should do nothing when updating a report shared in write mode without change`() =
        testDispatcherProvider.runTest {
            // given
            val reportEntity = ReportEntity(
                reference = "report-ref",
                tenantId = 123L,
                creatorId = 456L,
                displayName = "report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*")
            )
            coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L) } returns reportEntity
            coEvery { userRepository.findUsernameById(456L) } returns "the-user"
            coEvery { userRepository.findIdByUsername("the-user") } returns 456L
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1")) } returns setOf("campaign-key1")
            coEvery { campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any()) } returns listOf("campaign-key1")
            coEvery { campaignScenarioRepository.findNameByCampaignKeys(any()) } returns listOf("scenario-1", "scenario-2")
            coEvery { dataSeriesRepository.checkExistenceByTenantAndReference("the-tenant", any()) } returns true
            coEvery { dataSeriesRepository.findByReferenceAndTenant(any(), "the-tenant") } returns dataSeries[1]

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
                displayName = "report-name",
                sharingMode = SharingMode.WRITE,
                campaignKeys = listOf("campaign-key1"),
                campaignNamesPatterns = listOf("*")
            )

            // when
            val result = reportServiceImpl.update(tenant = "the-tenant", username = "the-user", reference =  "report-ref", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)

            // then
            assertThat(result).all {
                prop(Report::reference).isEqualTo("report-ref")
                prop(Report::creator).isEqualTo("the-user")
                prop(Report::displayName).isEqualTo("report-name")
                prop(Report::sharingMode).isEqualTo(SharingMode.WRITE)
                prop(Report::campaignKeys).hasSize(1)
                prop(Report::campaignNamesPatterns).hasSize(1)
                prop(Report::resolvedCampaignKeys).hasSize(1)
                prop(Report::scenarioNamesPatterns).hasSize(0)
                prop(Report::resolvedScenarioNames).hasSize(2)
                prop(Report::dataComponents).hasSize(0)
            }
            coVerifyOrder {
                reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L)
                userRepository.findUsernameById(456L)
                campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1"))
                campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any())
                campaignScenarioRepository.findNameByCampaignKeys(any())
            }
            coVerifyNever {
                reportRepository.update(any())
                reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
            }
        }

    @Test
    internal fun `should not update a report with a list of campaign keys that do not belong to the tenant`() =
        testDispatcherProvider.runTest {
            // given
            val reportEntity = ReportEntity(
                reference = "report-ref",
                tenantId = 123L,
                creatorId = 456L,
                displayName = "report-name"
            )
            coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L) } returns reportEntity
            coEvery { userRepository.findUsernameById(456L) } returns "the-user"
            coEvery { userRepository.findIdByUsername("the-user") } returns 456L
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1")) } returns emptySet()

            val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
                displayName = "report-name",
                campaignKeys = listOf("campaign-key1")
            )

            // when
            val exception = assertThrows<IllegalArgumentException> {
                reportServiceImpl.update(tenant = "the-tenant", username = "the-user", reference =  "report-ref", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)
            }

            // then
            assertThat(exception.message).isEqualTo(REPORT_CAMPAIGN_KEYS_NOT_ALLOWED)
        }

    @Test
    internal fun `should update the report when not shared and owned`() = testDispatcherProvider.runTest {
        // given
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
        coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L) } returns reportEntity
        coEvery { userRepository.findUsernameById(456L) } returns "the-user"
        coEvery { userRepository.findIdByUsername("the-user") } returns 456L
        coEvery { campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1")) } returns setOf("campaign-key1")
        coEvery { campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any()) } returns listOf("campaign-key1", "campaign-key2", "campaign-key3")
        coEvery { campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(any(), any()) } returns listOf("scenario-1", "scenario-2", "scenario-3")
        coEvery { dataSeriesRepository.checkExistenceByTenantAndReference("the-tenant", any()) } returns true
        coEvery { dataSeriesRepository.findByReferenceAndTenant("series-ref-2", "the-tenant") } returns dataSeries[1]
        coEvery { reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>()) } returns flowOf(
            ReportDataComponentEntity(id = 1, reportId = -1, type = DataComponentType.DIAGRAM, dataSeries = listOf(dataSeries[1]))
        )
        coEvery { reportDataComponentRepository.deleteByReportId(any()) } returns 0
        coEvery { reportRepository.update(any()) } returnsArgument 0

        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "new-report-name",
            sharingMode = SharingMode.READONLY,
            campaignKeys = listOf("campaign-key1"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            dataComponents = listOf(DiagramCreationAndUpdateRequest(dataSeriesReferences = listOf("series-ref-2")))
        )

        // when
        val result = reportServiceImpl.update(tenant = "the-tenant", username = "the-user", reference =  "report-ref", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)

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
            prop(Report::resolvedCampaignKeys).all {
                hasSize(3)
                containsOnly("campaign-key1", "campaign-key2", "campaign-key3")
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
            userRepository.findIdByUsername("the-user")
            reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L)
            userRepository.findUsernameById(456L)
            campaignRepository.findKeyByTenantAndKeyIn("the-tenant", listOf("campaign-key1"))
            reportDataComponentRepository.deleteByReportId(any())
            reportRepository.update(any())
            reportDataComponentRepository.saveAll(any<Iterable<ReportDataComponentEntity>>())
            campaignRepository.findKeysByTenantAndNamePatterns("the-tenant", any())
            campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(any(), any())
            userRepository.findUsernameById(456L)
        }
    }

    @Test
    internal fun `should not update the report when not shared in write mode and not owned`() = testDispatcherProvider.runTest {
        // given
        coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L) } returns null
        coEvery { userRepository.findIdByUsername("the-user") } returns 456L

        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "new-report-name",
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            reportServiceImpl.update(tenant = "the-tenant", username = "the-user", reference =  "report-ref", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_UPDATE_DENY)
    }

    @Test
    internal fun `should not update the report when not shared and not owned`() = testDispatcherProvider.runTest {
        // given
        coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 123L) } returns null
        coEvery { userRepository.findIdByUsername("other-user") } returns 123L

        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "new-report-name",
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            reportServiceImpl.update(tenant = "the-tenant", username = "other-user", reference =  "report-ref", reportCreationAndUpdateRequest = reportCreationAndUpdateRequest)
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_UPDATE_DENY)
    }

    @Test
    internal fun `should delete the report when shared in write mode`() = testDispatcherProvider.runTest {
        // given
        val reportEntity = ReportEntity(
            reference = "report-ref",
            tenantId = -1,
            creatorId = 456L,
            displayName = "report-name",
            sharingMode = SharingMode.WRITE,
            campaignNamesPatterns = listOf("*"),
        )
        coEvery { reportRepository.delete(any()) } returns 1
        coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 123L) } returns reportEntity
        coEvery { userRepository.findIdByUsername("other-user") } returns 123L

        // when
        reportServiceImpl.delete(tenant = "the-tenant", username = "other-user", reference = "report-ref")

        // then
        coVerifyOrder {
            userRepository.findIdByUsername("other-user")
            reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 123L)
            reportRepository.delete(reportEntity)
        }
    }

    @Test
    internal fun `should delete the report when owned if not shared`() = testDispatcherProvider.runTest {
        // given
        val reportEntity = ReportEntity(
            reference = "report-ref",
            tenantId = -1,
            creatorId = 456L,
            displayName = "report-name",
            sharingMode = SharingMode.NONE,
            campaignNamesPatterns = listOf("*"),
        )
        coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L) } returns reportEntity
        coEvery { reportRepository.delete(any()) } returns 1
        coEvery { userRepository.findIdByUsername("the-user") } returns 456L

        // when
        reportServiceImpl.delete(tenant = "the-tenant", username = "the-user", reference = "report-ref")

        // then
        coVerifyOrder {
            userRepository.findIdByUsername("the-user")
            reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 456L)
            reportRepository.delete(reportEntity)
        }
    }

    @Test
    internal fun `should not delete the report when not owned not shared in write mode`() = testDispatcherProvider.runTest {
        // given
        coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = 123L) } returns null
        coEvery { userRepository.findIdByUsername("other-user") } returns 123L
        coEvery { reportRepository.delete(any()) } returns 1

        // when
        val exception = assertThrows<IllegalArgumentException> {
            reportServiceImpl.delete(tenant = "the-tenant", username = "other-user", reference = "report-ref")
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_DELETE_DENY)
        coVerifyNever {
            reportRepository.delete(any())
        }
    }

    @Test
    internal fun `should not delete the report when not owned if not shared`() = testDispatcherProvider.runTest {
        // given
        val creatorId = 456L
        coEvery { reportRepository.getReportIfUpdatable(tenant = "the-tenant", reference = "report-ref", creatorId = creatorId) } returns null
        coEvery { userRepository.findIdByUsername("other-user") } returns creatorId

        // when
        val exception = assertThrows<IllegalArgumentException> {
            reportServiceImpl.delete(tenant = "the-tenant", username = "other-user", reference = "report-ref")
        }

        // then
        assertThat(exception.message).isEqualTo(REPORT_DELETE_DENY)
        coVerifyNever {
            reportRepository.delete(any())
        }
    }
}