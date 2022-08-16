package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.hasSize
import assertk.assertions.prop
import io.micronaut.data.exceptions.DataAccessException
import io.qalipsis.api.report.query.QueryAggregationOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.report.SharingMode
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Instant


/**
 * @author Joël Valère
 */
internal class ReportRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var reportRepository: ReportRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var userRepository: UserRepository

    @Inject
    private lateinit var dataSeriesRepository: DataSeriesRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var reportDataComponentRepository: ReportDataComponentRepository

    private val tenantPrototype = TenantEntity(reference = "my-tenant", displayName = "test-tenant")

    private val userPrototype = UserEntity(username = "my-user", displayName = "User for test")

    private val reportPrototype =
        ReportEntity(
            reference = "report-ref",
            tenantId = -1,
            creatorId = -1,
            displayName = "my-report-name",
            campaignKeys = listOf("campaign-key1", "campaign-key2"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            query = "This is the query"
        )

    private val dataComponents = listOf(
        ReportDataComponentEntity(
            reportId = -1,
            type = DataComponentType.DIAGRAM,
            dataSeries = emptyList()
        ),
        ReportDataComponentEntity(
            reportId = -1,
            type = DataComponentType.DATA_TABLE,
            dataSeries = emptyList()
        )
    )

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        reportDataComponentRepository.deleteAll()
        reportRepository.deleteAll()
        dataSeriesRepository.deleteAll()
        campaignRepository.deleteAll()
        tenantRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `should save and retrieve a report with minimal information`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val saved = reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
            )
        )

        // when
        val fetched = reportRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(ReportEntity::id).isEqualTo(saved.id)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.READONLY)
            prop(ReportEntity::query).isNull()
            prop(ReportEntity::campaignKeys).isEmpty()
            prop(ReportEntity::campaignNamesPatterns).isEmpty()
            prop(ReportEntity::scenarioNamesPatterns).isEmpty()
            prop(ReportEntity::dataComponents).isEmpty()
        }
    }

    @Test
    fun `should save and retrieve a complete report`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val reportSaved = reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
                sharingMode = SharingMode.WRITE,
                query = "This is the query",
                campaignKeys = listOf("camp-1", "camp-2"),
                campaignNamesPatterns = listOf("camp-1*", "*camp-2"),
                scenarioNamesPatterns = listOf("scen-1*", "*scen-2")
            )
        )
        val dataSeriesEntities = dataSeriesRepository.saveAll(
            dataComponents.map { reportDataComponentEntity ->
                DataSeriesEntity(
                    reference = "my-series_${reportDataComponentEntity.type}",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name_${reportDataComponentEntity.type}",
                    dataType = DataType.METERS
                )
            }
        ).toList()
        reportDataComponentRepository.saveAll(
            dataComponents.mapIndexed { index, reportDataComponentEntity ->
                reportDataComponentEntity.copy(reportId = reportSaved.id, dataSeries = listOf(dataSeriesEntities[index]))
            }
        ).toList()

        // when
        val fetchedWithPartialDataComponents = reportRepository.findById(reportSaved.id)!!
        val dataComponentEntities = reportDataComponentRepository.findByIdInOrderById(fetchedWithPartialDataComponents.dataComponents.map { it.id }).toList()
        val fetchedWithFullDataComponents = fetchedWithPartialDataComponents.copy(dataComponents = dataComponentEntities)

        // then
        assertThat(fetchedWithFullDataComponents).isNotNull().all {
            prop(ReportEntity::id).isEqualTo(reportSaved.id)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.WRITE)
            prop(ReportEntity::query).isEqualTo("This is the query")
            prop(ReportEntity::campaignKeys).containsOnly("camp-1", "camp-2")
            prop(ReportEntity::campaignNamesPatterns).containsOnly("camp-1*", "*camp-2")
            prop(ReportEntity::scenarioNamesPatterns).containsOnly("scen-1*", "*scen-2")
            prop(ReportEntity::dataComponents).all {
                hasSize(2)
                index(0).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DIAGRAM)
                    prop(ReportDataComponentEntity::dataSeries).all {
                        hasSize(1)
                    }
                }
                index(1).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DATA_TABLE)
                    prop(ReportDataComponentEntity::dataSeries).all {
                        hasSize(1)
                        index(0).all{
                            prop(DataSeriesEntity::reference).isEqualTo("my-series_DATA_TABLE")
                            prop(DataSeriesEntity::tenantId).isEqualTo(tenant.id)
                            prop(DataSeriesEntity::creatorId).isEqualTo(creator.id)
                            prop(DataSeriesEntity::displayName).isEqualTo("my-name_DATA_TABLE")
                            prop(DataSeriesEntity::dataType).isEqualTo(DataType.METERS)
                            prop(DataSeriesEntity::filters).isEmpty()
                            prop(DataSeriesEntity::color).isNull()
                            prop(DataSeriesEntity::fieldName).isNull()
                            prop(DataSeriesEntity::aggregationOperation).isEqualTo(QueryAggregationOperator.COUNT)
                            prop(DataSeriesEntity::timeframeUnitMs).isNull()
                            prop(DataSeriesEntity::displayFormat).isNull()
                            prop(DataSeriesEntity::query).isNull()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should not save two reports with the same name in the tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(
            ReportEntity(
                reference = "my-report-1",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "report-name",
            )
        )

        // when
        assertThrows<DataAccessException> {
            reportRepository.save(
                ReportEntity(
                    reference = "my-report-2",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "report-name",
                )
            )
        }

        // then
        assertThat(reportRepository.findAll().count()).isEqualTo(1)
    }

    @Test
    fun `should not save two reports with same reference in the tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "report-name-1"
            )
        )

        // when
        assertThrows<DataAccessException> {
            reportRepository.save(
                ReportEntity(
                    reference = "report-ref",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "report-name-2",
                )
            )
        }

        // then
        assertThat(reportRepository.findAll().count()).isEqualTo(1)
    }

    @Test
    fun `should save two reports with same reference and name in different tenants`() = testDispatcherProvider.run {
        // given
        val tenant1 = tenantRepository.save(tenantPrototype.copy())
        val tenant2 = tenantRepository.save(tenantPrototype.copy(reference = "other-tenant"))
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(
            ReportEntity(
                reference = "my-series",
                tenantId = tenant1.id,
                creatorId = creator.id,
                displayName = "my-name",
            )
        )

        // when
        assertDoesNotThrow {
            reportRepository.save(
                ReportEntity(
                    reference = "my-series",
                    tenantId = tenant2.id,
                    creatorId = creator.id,
                    displayName = "my-name",
                )
            )
        }

        // then
        assertThat(reportRepository.findAll().count()).isEqualTo(2)
    }

    @Test
    fun `should save a report then update and fetch by reference and tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val reportSaved = reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id
            )
        )
        val dataSeriesEntities = dataSeriesRepository.saveAll(
            dataComponents.map { reportDataComponentEntity ->
                DataSeriesEntity(
                    reference = "my-series_${reportDataComponentEntity.type}",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name_${reportDataComponentEntity.type}",
                    dataType = DataType.METERS
                )
            }
        ).toList()

        reportDataComponentRepository.saveAll(
            dataComponents.mapIndexed { index, reportDataComponentEntity ->
                reportDataComponentEntity.copy(reportId = reportSaved.id, dataSeries = listOf(dataSeriesEntities[index]))
            }
        ).toList()

        // when
        val fetchedWithPartialDataComponents = reportRepository.findById(reportSaved.id)!!
        val dataComponentEntities = reportDataComponentRepository.findByIdInOrderById(fetchedWithPartialDataComponents.dataComponents.map { it.id }).toList()
        val fetchedWithFullDataComponents = fetchedWithPartialDataComponents.copy(dataComponents = dataComponentEntities)

        // then
        assertThat(fetchedWithFullDataComponents).isNotNull().all {
            prop(ReportEntity::id).isEqualTo(reportSaved.id)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-name")
            prop(ReportEntity::query).isEqualTo("This is the query")
            prop(ReportEntity::campaignKeys).hasSize(2)
            prop(ReportEntity::campaignNamesPatterns).hasSize(2)
            prop(ReportEntity::scenarioNamesPatterns).hasSize(1)
            prop(ReportEntity::dataComponents).all {
                hasSize(2)
                index(0).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DIAGRAM)
                    prop(ReportDataComponentEntity::dataSeries).all {
                        hasSize(1)
                    }
                }
                index(1).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DATA_TABLE)
                    prop(ReportDataComponentEntity::dataSeries).all {
                        hasSize(1)
                    }
                }
            }
        }

        // Update step
        val updated = fetchedWithPartialDataComponents.copy(
            displayName = "my-report-update-name",
            query = "This is the updated query",
            dataComponents = emptyList(),
            campaignKeys = emptyList()
        )
        val beforeUpdate = Instant.now()

        // when
        reportRepository.update(updated)
        reportDataComponentRepository.deleteByReportId(updated.id)
        dataSeriesRepository.deleteAll()
        val dataSeries = dataSeriesRepository.saveAll(
            listOf(
                DataSeriesEntity(
                    reference = "my-series-update",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name-update",
                    dataType = DataType.EVENTS
                ),
                DataSeriesEntity(
                    reference = "my-series-update-2",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name-update-2",
                    dataType = DataType.METERS
                )
            )
        ).toList()
        reportDataComponentRepository.save(
            dataComponents[0].copy(reportId = updated.id, dataSeries = dataSeries)
        )
        val fetchUpdatedWithPartialDataComponents =
            reportRepository.findByTenantAndReference(tenant = "my-tenant", reference = "report-ref")
        val newDataComponentEntities =
            reportDataComponentRepository.findByIdInOrderById(fetchUpdatedWithPartialDataComponents.dataComponents.map { it.id }).toList()
        val fetchedUpdatedWithFullDataComponents = fetchUpdatedWithPartialDataComponents.copy(dataComponents = newDataComponentEntities)
        val newDataSeriesEntities = dataSeriesRepository.findAll().toList()

        // then
        //Make sure that data series exist in DB
        assertThat(newDataSeriesEntities).all {
            hasSize(2)
        }
        assertThat(fetchedUpdatedWithFullDataComponents).isNotNull().all {
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::version).isGreaterThanOrEqualTo(beforeUpdate)
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-update-name")
            prop(ReportEntity::query).isEqualTo("This is the updated query")
            prop(ReportEntity::campaignKeys).hasSize(0)
            prop(ReportEntity::campaignNamesPatterns).hasSize(2)
            prop(ReportEntity::scenarioNamesPatterns).hasSize(1)
            prop(ReportEntity::dataComponents).all {
                hasSize(1)
                index(0).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DIAGRAM)
                    prop(ReportDataComponentEntity::dataSeries).all {
                        hasSize(2)
                        index(1).all{
                            prop(DataSeriesEntity::reference).isEqualTo("my-series-update-2")
                            prop(DataSeriesEntity::tenantId).isEqualTo(tenant.id)
                            prop(DataSeriesEntity::creatorId).isEqualTo(creator.id)
                            prop(DataSeriesEntity::displayName).isEqualTo("my-name-update-2")
                            prop(DataSeriesEntity::dataType).isEqualTo(DataType.METERS)
                            prop(DataSeriesEntity::filters).isEmpty()
                            prop(DataSeriesEntity::color).isNull()
                            prop(DataSeriesEntity::fieldName).isNull()
                            prop(DataSeriesEntity::aggregationOperation).isEqualTo(QueryAggregationOperator.COUNT)
                            prop(DataSeriesEntity::timeframeUnitMs).isNull()
                            prop(DataSeriesEntity::displayFormat).isNull()
                            prop(DataSeriesEntity::query).isNull()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should delete a report when the tenant is deleted`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(reportPrototype.copy(tenantId = tenant.id, creatorId = creator.id))
        assertThat(reportRepository.findAll().count()).isEqualTo(1)

        // when
        tenantRepository.deleteById(tenant.id)

        // then
        assertThat(reportRepository.findAll().count()).isEqualTo(0)
    }

    @Test
    fun `should throw an error when trying to delete a report with a deleted creator`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(reportPrototype.copy(tenantId = tenant.id, creatorId = creator.id))
        assertThat(reportRepository.findAll().count()).isEqualTo(1)

        // when
        assertThrows<DataAccessException> {
            userRepository.deleteById(creator.id)
        }

        // then
        assertThat(reportRepository.findAll().count()).isEqualTo(1)
    }

    @Test
    fun `should preserve data components persistence order`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val reportSaved = reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name"
            )
        )
        val reportSaved2 = reportRepository.save(
            ReportEntity(
                reference = "report-ref-2",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-2"
            )
        )
        val dataSeriesEntities = dataSeriesRepository.saveAll(
            (1..1000).map { index ->
                DataSeriesEntity(
                    reference = "my-series_${index - 1}",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name_${index - 1}",
                    dataType = DataType.METERS
                )
            }
        ).toList()
        reportDataComponentRepository.saveAll(
            (1..1000).map { index ->
                ReportDataComponentEntity(
                    id = index.toLong(),
                    type = if ((index % 2) == 0) DataComponentType.DIAGRAM else DataComponentType.DATA_TABLE,
                    reportId = reportSaved.id,
                    dataSeries = listOf(dataSeriesEntities[index - 1])
                )
            }.toList().plus(
                (1001..2000).map { index2 ->
                    ReportDataComponentEntity(
                        id = index2.toLong(),
                        type = if ((index2 % 2) == 0) DataComponentType.DATA_TABLE else DataComponentType.DIAGRAM,
                        reportId = reportSaved2.id,
                        dataSeries = listOf(dataSeriesEntities[index2 - 1001])
                    )
                }
            )
        ).toList()

        // when
        val fetchedWithPartialDataComponents = reportRepository.findById(reportSaved.id)!!
        val dataComponentEntities = reportDataComponentRepository.findByIdInOrderById(fetchedWithPartialDataComponents.dataComponents.map { it.id }).toList()
        val fetchedWithFullDataComponents = fetchedWithPartialDataComponents.copy(dataComponents = dataComponentEntities)

        // then
        assertThat(fetchedWithFullDataComponents).isNotNull().all {
            prop(ReportEntity::id).isEqualTo(reportSaved.id)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.READONLY)
            prop(ReportEntity::query).isNull()
            prop(ReportEntity::campaignKeys).isEmpty()
            prop(ReportEntity::campaignNamesPatterns).isEmpty()
            prop(ReportEntity::scenarioNamesPatterns).isEmpty()
            prop(ReportEntity::dataComponents).all {
                hasSize(1000)
                repeat(1000){ index ->
                    index(index).all {
                        val expectedType = if ((index % 2) == 0) DataComponentType.DATA_TABLE else DataComponentType.DIAGRAM
                        prop(ReportDataComponentEntity::type).isEqualTo(expectedType)
                        prop(ReportDataComponentEntity::dataSeries).all {
                            hasSize(1)
                            index(0).all {
                                prop(DataSeriesEntity::reference).isEqualTo("my-series_${index}")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should preserve data components persistence order even when in reverse order`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val reportSaved = reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name"
            )
        )
        val dataSeriesEntities = dataSeriesRepository.saveAll(
            (1..1000).map { index ->
                DataSeriesEntity(
                    reference = "my-series_${index - 1}",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name_${index - 1}",
                    dataType = DataType.METERS
                )
            }
        ).toList()
        reportDataComponentRepository.saveAll(
            (1..1000).map { index ->
                ReportDataComponentEntity(
                    id = index.toLong(),
                    type = if ((index % 2) == 0) DataComponentType.DIAGRAM else DataComponentType.DATA_TABLE,
                    reportId = reportSaved.id,
                    dataSeries = listOf(dataSeriesEntities[index - 1])
                )
            }
        ).toList()

        // when
        val fetchedWithPartialDataComponents = reportRepository.findById(reportSaved.id)!!
        val dataComponentEntities = reportDataComponentRepository.findByIdInOrderById(fetchedWithPartialDataComponents.dataComponents.map { it.id }).toList()
        val fetchedWithFullDataComponents = fetchedWithPartialDataComponents.copy(dataComponents = dataComponentEntities.reversed())

        // then
        assertThat(fetchedWithFullDataComponents).isNotNull().all {
            prop(ReportEntity::id).isEqualTo(reportSaved.id)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.READONLY)
            prop(ReportEntity::query).isNull()
            prop(ReportEntity::campaignKeys).isEmpty()
            prop(ReportEntity::campaignNamesPatterns).isEmpty()
            prop(ReportEntity::scenarioNamesPatterns).isEmpty()
            prop(ReportEntity::dataComponents).all {
                hasSize(1000)
                repeat(1000){ index ->
                    index(index).all {
                        val expectedType = if ((index % 2) == 0) DataComponentType.DIAGRAM else DataComponentType.DATA_TABLE
                        val dataSeriesIndex = dataComponentEntities.size - 1 - index
                        prop(ReportDataComponentEntity::type).isEqualTo(expectedType)
                        prop(ReportDataComponentEntity::dataSeries).all {
                            hasSize(1)
                            index(0).all {
                                prop(DataSeriesEntity::reference).isEqualTo("my-series_${dataSeriesIndex}")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should retrieve and existing a report when owned`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val saved = reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
                sharingMode = SharingMode.NONE
            )
        )

        // when
        val fetched = reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "my-tenant", reference = "report-ref", creatorId = creator.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(ReportEntity::id).isEqualTo(saved.id)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.NONE)
            prop(ReportEntity::query).isNull()
            prop(ReportEntity::campaignKeys).isEmpty()
            prop(ReportEntity::campaignNamesPatterns).isEmpty()
            prop(ReportEntity::scenarioNamesPatterns).isEmpty()
            prop(ReportEntity::dataComponents).isEmpty()
        }
    }

    @Test
    fun `should retrieve and existing a report when not owned but shared`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val creator2 = userRepository.save(userPrototype.copy(username = "other-user"))
        val saved = reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
                sharingMode = SharingMode.READONLY
            )
        )

        // when
        val fetched = reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "my-tenant", reference = "report-ref", creatorId = creator2.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(ReportEntity::id).isEqualTo(saved.id)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.READONLY)
            prop(ReportEntity::query).isNull()
            prop(ReportEntity::campaignKeys).isEmpty()
            prop(ReportEntity::campaignNamesPatterns).isEmpty()
            prop(ReportEntity::scenarioNamesPatterns).isEmpty()
            prop(ReportEntity::dataComponents).isEmpty()
        }
    }

    @Test
    fun `should not retrieve and existing a report when not owned and not shared`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val creator2 = userRepository.save(userPrototype.copy(username = "other-user"))
        reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
                sharingMode = SharingMode.NONE
            )
        )

        // when + then
        assertThrows<DataAccessException> {
            reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = "my-tenant", reference = "report-ref", creatorId = creator2.id)
        }
    }

    @Test
    fun `should retrieve and existing a report when not owned and shared in write`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val creator2 = userRepository.save(userPrototype.copy(username = "other-user"))
        val saved = reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
                sharingMode = SharingMode.WRITE
            )
        )

        // when
        val fetched = reportRepository.getReportIfUpdatable(tenant = "my-tenant", reference = "report-ref", creatorId = creator2.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(ReportEntity::id).isEqualTo(saved.id)
            prop(ReportEntity::reference).isEqualTo("report-ref")
            prop(ReportEntity::tenantId).isEqualTo(tenant.id)
            prop(ReportEntity::creatorId).isEqualTo(creator.id)
            prop(ReportEntity::displayName).isEqualTo("my-report-name")
            prop(ReportEntity::sharingMode).isEqualTo(SharingMode.WRITE)
            prop(ReportEntity::query).isNull()
            prop(ReportEntity::campaignKeys).isEmpty()
            prop(ReportEntity::campaignNamesPatterns).isEmpty()
            prop(ReportEntity::scenarioNamesPatterns).isEmpty()
            prop(ReportEntity::dataComponents).isEmpty()
        }
    }

    @Test
    fun `should not retrieve and existing a report when not owned and not shared in write`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val creator2 = userRepository.save(userPrototype.copy(username = "other-user"))
        reportRepository.save(
            ReportEntity(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
                sharingMode = SharingMode.NONE
            )
        )

        // when + then
        assertThrows<DataAccessException> {
            reportRepository.getReportIfUpdatable(tenant = "my-tenant", reference = "report-ref", creatorId = creator2.id)
        }
    }
}