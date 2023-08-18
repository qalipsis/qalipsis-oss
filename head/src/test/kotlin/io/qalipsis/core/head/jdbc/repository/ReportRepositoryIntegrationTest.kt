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

package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.report.SharingMode
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
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

        // when searching a report with same name but other ID
        var existsWithSameNameAndOtherId =
            reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(tenant.reference, "my-report-name", saved.id)

        // then
        assertThat(existsWithSameNameAndOtherId).isFalse()

        // when searching a report with other name and other ID
        existsWithSameNameAndOtherId = reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
            tenant.reference,
            "my-other-report-name",
            saved.id
        )

        // then
        assertThat(existsWithSameNameAndOtherId).isFalse()

        // when searching a report with same name and any ID
        existsWithSameNameAndOtherId =
            reportRepository.existsByTenantReferenceAndDisplayNameAndIdNot(tenant.reference, "my-report-name")

        // then
        assertThat(existsWithSameNameAndOtherId).isTrue()
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
                    dataType = DataType.METERS,
                    valueName = "my-value",
                )
            }
        ).toList()
        reportDataComponentRepository.saveAll(
            dataComponents.mapIndexed { index, reportDataComponentEntity ->
                reportDataComponentEntity.copy(
                    reportId = reportSaved.id,
                    dataSeries = listOf(dataSeriesEntities[index])
                )
            }
        ).toList()

        // when
        val fetchedWithPartialDataComponents = reportRepository.findById(reportSaved.id)!!
        val dataComponentEntities =
            reportDataComponentRepository.findByIdInOrderById(fetchedWithPartialDataComponents.dataComponents.map { it.id })
                .toList()
        val fetchedWithFullDataComponents =
            fetchedWithPartialDataComponents.copy(dataComponents = dataComponentEntities)

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
                        index(0).all {
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
                            prop(DataSeriesEntity::colorOpacity).isNull()
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
        assertThrows<R2dbcDataIntegrityViolationException> {
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
        assertThrows<R2dbcDataIntegrityViolationException> {
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
                    dataType = DataType.METERS,
                    valueName = "my-value",
                )
            }
        ).toList()

        reportDataComponentRepository.saveAll(
            dataComponents.mapIndexed { index, reportDataComponentEntity ->
                reportDataComponentEntity.copy(
                    reportId = reportSaved.id,
                    dataSeries = listOf(dataSeriesEntities[index])
                )
            }
        ).toList()

        // when
        val fetchedWithPartialDataComponents = reportRepository.findById(reportSaved.id)!!
        val dataComponentEntities =
            reportDataComponentRepository.findByIdInOrderById(fetchedWithPartialDataComponents.dataComponents.map { it.id })
                .toList()
        val fetchedWithFullDataComponents =
            fetchedWithPartialDataComponents.copy(dataComponents = dataComponentEntities)

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
                    dataType = DataType.EVENTS,
                    valueName = "my-value",
                ),
                DataSeriesEntity(
                    reference = "my-series-update-2",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name-update-2",
                    dataType = DataType.METERS,
                    valueName = "my-value",
                )
            )
        ).toList()
        reportDataComponentRepository.save(
            dataComponents[0].copy(reportId = updated.id, dataSeries = dataSeries)
        )
        val fetchUpdatedWithPartialDataComponents =
            reportRepository.findByTenantAndReference(tenant = "my-tenant", reference = "report-ref")
        val newDataComponentEntities =
            reportDataComponentRepository.findByIdInOrderById(fetchUpdatedWithPartialDataComponents.dataComponents.map { it.id })
                .toList()
        val fetchedUpdatedWithFullDataComponents =
            fetchUpdatedWithPartialDataComponents.copy(dataComponents = newDataComponentEntities)
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
                        index(1).all {
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
                            prop(DataSeriesEntity::colorOpacity).isNull()
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
        assertThrows<R2dbcDataIntegrityViolationException> {
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
                    dataType = DataType.METERS,
                    valueName = "my-value",
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
        val dataComponentEntities =
            reportDataComponentRepository.findByIdInOrderById(fetchedWithPartialDataComponents.dataComponents.map { it.id })
                .toList()
        val fetchedWithFullDataComponents =
            fetchedWithPartialDataComponents.copy(dataComponents = dataComponentEntities)

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
                repeat(1000) { index ->
                    index(index).all {
                        val expectedType =
                            if ((index % 2) == 0) DataComponentType.DATA_TABLE else DataComponentType.DIAGRAM
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
                    dataType = DataType.METERS,
                    valueName = "my-value",
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
        val dataComponentEntities =
            reportDataComponentRepository.findByIdInOrderById(fetchedWithPartialDataComponents.dataComponents.map { it.id })
                .toList()
        val fetchedWithFullDataComponents =
            fetchedWithPartialDataComponents.copy(dataComponents = dataComponentEntities.reversed())

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
                repeat(1000) { index ->
                    index(index).all {
                        val expectedType =
                            if ((index % 2) == 0) DataComponentType.DIAGRAM else DataComponentType.DATA_TABLE
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
        val fetched = reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
            tenant = "my-tenant",
            reference = "report-ref",
            creatorId = creator.id
        )

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
        val fetched = reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
            tenant = "my-tenant",
            reference = "report-ref",
            creatorId = creator2.id
        )

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
        val report = reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(
            tenant = "my-tenant",
            reference = "report-ref",
            creatorId = creator2.id
        )

        // then
        Assertions.assertNull(report)
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
        val fetched = reportRepository.getReportIfUpdatable(
            tenant = "my-tenant",
            reference = "report-ref",
            creatorId = creator2.id
        )

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
    fun `should not retrieve and existing a report when not owned and not shared in write`() =
        testDispatcherProvider.run {
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

            // when
            val report = reportRepository.getReportIfUpdatable(
                tenant = "my-tenant",
                reference = "report-ref",
                creatorId = creator2.id
            )

            // then
            Assertions.assertNull(report)
        }

    @Test
    fun `should fetch all reports saved with the default params`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val reportEntity = reportRepository.save(reportPrototype.copy(tenantId = tenant.id, creatorId = creator.id))
        assertThat(reportRepository.findAll().count()).isEqualTo(1)

        //when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference, creator.username, Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).containsExactly(reportEntity)
    }

    @Test
    fun `should fetch all reports and sort them by the sharing mode`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val reportWithWriteSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        val reportWithNoSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-2",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-2",
                sharingMode = SharingMode.NONE
            )
        )
        val reportWithReadSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-3",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-3"
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(3)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference, creator.username, Pageable.from(0, 3, Sort.of(Sort.Order("sharingMode")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(3)
            containsExactly(reportWithNoSharingMode, reportWithReadSharingMode, reportWithWriteSharingMode)
        }
    }

    @Test
    fun `should reproduce the current state of cloud db and fetch all reports without filters`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val report1 = reportRepository.save(
                reportPrototype.copy(
                    reference = "79wbqxlndc",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "New campaign pattern",
                    sharingMode = SharingMode.WRITE,
                    campaignNamesPatterns = listOf("New Campaign")
                )
            )
            val report2 = reportRepository.save(
                reportPrototype.copy(
                    reference = "79gpezu117",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "Spike on Login",
                    sharingMode = SharingMode.WRITE,
                    campaignNamesPatterns = listOf("Spike on Login")
                )
            )
            val report3 = reportRepository.save(
                reportPrototype.copy(
                    reference = "36i6jlv586",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "A new report",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:clhjj1y4900000j84rdg9iq3t",
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report4 = reportRepository.save(
                reportPrototype.copy(
                    reference = "88jgiz1x2d",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "This is a new one",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:clhjj1y4900000j84rdg9iq3t",
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report5 = reportRepository.save(
                reportPrototype.copy(
                    reference = "847vmsn2sk",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "kM123",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:cldvuhx9z00240120mmssl0r9",
                        "test-tenant-1:cled7a4u6000001758kw1qmgf",
                        "test-tenant-1:clhjj1y4900000j84rdg9iq3t"
                    )
                )
            )
            val report6 = reportRepository.save(
                reportPrototype.copy(
                    reference = "555thldnmk",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "KM Report 9527",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report7 = reportRepository.save(
                reportPrototype.copy(
                    reference = "55bf8uffsg",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "KM Report T456",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report8 = reportRepository.save(
                reportPrototype.copy(
                    reference = "55ehn6lr9p",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "KM Report T789",
                    sharingMode = SharingMode.READONLY,
                    campaignKeys = listOf(
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report9 = reportRepository.save(
                reportPrototype.copy(
                    reference = "98wekd5bdg",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "New Report",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:cled7a4u6000001758kw1qmgf",
                        "test-tenant-1:cld63tq44000b0120cqwf3pmu",
                        "test-tenant-1:cld78m3p1000i0120k9q5q3bw"
                    )
                )
            )
            val report10 = reportRepository.save(
                reportPrototype.copy(
                    reference = "646mgyfwj0",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "Do nothing",
                    campaignKeys = listOf(
                        "test-tenant-1:cliosq48c00080j55ckax3j00"
                    )
                )
            )
            reportRepository.save(
                reportPrototype.copy(
                    reference = "32v8i26rgx",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "test campaignpatterns",
                    sharingMode = SharingMode.READONLY,
                    campaignNamesPatterns = listOf(
                        "Checkout stress test",
                        "Spike on Login"
                    )
                )
            )

            // Save related data components.
            val dataComponentsForReport8 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report8.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DATA_TABLE,
                        reportId = report8.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            val dataComponentsForReport10 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DATA_TABLE,
                        reportId = report10.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report10.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            val dataComponentsForReport6 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report6.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DATA_TABLE,
                        reportId = report6.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report6.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            val dataComponentsForReport5 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DATA_TABLE,
                        reportId = report5.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report5.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            val dataComponentsForReport7 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report7.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            assertThat(reportRepository.findAll().count()).isEqualTo(11)


            // When searching and sort on displayName ascendant.
            var result = reportRepository.searchReports(
                "my-tenant",
                "my-user",
                Pageable.from(0, 4, Sort.of(Sort.Order.asc("displayName", true)))
            ).content.map { reportRepository.findById(it)!! } // We are sure that the report exists.
            // then
            assertThat(result).all {
                hasSize(4)
                containsExactly(
                    report3,
                    report10.copy(
                        dataComponents = dataComponentsForReport10
                    ),
                    report6.copy(
                        dataComponents = dataComponentsForReport6
                    ),
                    report7.copy(
                        dataComponents = dataComponentsForReport7
                    )
                )
            }

            // When searching and sort on reference descendant.
            result = reportRepository.searchReports(
                "my-tenant",
                "my-user",
                Pageable.from(0, 10, Sort.of(Sort.Order.desc("reference", true)))
            ).content.map { reportRepository.findById(it)!! } // We are sure that the report exists.
            // then
            assertThat(result).all {
                hasSize(10)
                containsExactly(
                    report9,
                    report4,
                    report5.copy(
                        dataComponents = dataComponentsForReport5
                    ),
                    report1,
                    report2,
                    report10.copy(
                        dataComponents = dataComponentsForReport10
                    ),
                    report8.copy(
                        dataComponents = dataComponentsForReport8
                    ),
                    report7.copy(
                        dataComponents = dataComponentsForReport7
                    ),
                    report6.copy(
                        dataComponents = dataComponentsForReport6
                    ),
                    report3
                )
            }


            // When searching on the second page with case-insensitive.
            result = reportRepository.searchReports(
                "my-tenant",
                "my-user",
                Pageable.from(1, 4, Sort.of(Sort.Order.asc("displayName", true)))
            ).content.map { reportRepository.findById(it)!! } // We are sure that the report exists.
            // then
            assertThat(result).all {
                hasSize(4)
                containsExactly(
                    report8.copy(
                        dataComponents = dataComponentsForReport8
                    ),
                    report5.copy(
                        dataComponents = dataComponentsForReport5
                    ),
                    report1,
                    report9
                )
            }
        }

    @Test
    fun `should retrieve a list of reports based on their internal IDs`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val report1 = reportRepository.save(
                reportPrototype.copy(
                    reference = "79wbqxlndc",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "New campaign pattern",
                    sharingMode = SharingMode.WRITE,
                    campaignNamesPatterns = listOf("New Campaign")
                )
            )
            val report2 = reportRepository.save(
                reportPrototype.copy(
                    reference = "79gpezu117",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "Spike on Login",
                    sharingMode = SharingMode.WRITE,
                    campaignNamesPatterns = listOf("Spike on Login")
                )
            )
            val report3 = reportRepository.save(
                reportPrototype.copy(
                    reference = "36i6jlv586",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "A new report",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:clhjj1y4900000j84rdg9iq3t",
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report4 = reportRepository.save(
                reportPrototype.copy(
                    reference = "88jgiz1x2d",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "This is a new one",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:clhjj1y4900000j84rdg9iq3t",
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report5 = reportRepository.save(
                reportPrototype.copy(
                    reference = "847vmsn2sk",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "kM123",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:cldvuhx9z00240120mmssl0r9",
                        "test-tenant-1:cled7a4u6000001758kw1qmgf",
                        "test-tenant-1:clhjj1y4900000j84rdg9iq3t"
                    )
                )
            )
            val report6 = reportRepository.save(
                reportPrototype.copy(
                    reference = "555thldnmk",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "KM Report 9527",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report7 = reportRepository.save(
                reportPrototype.copy(
                    reference = "55bf8uffsg",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "KM Report T456",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report8 = reportRepository.save(
                reportPrototype.copy(
                    reference = "55ehn6lr9p",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "KM Report T789",
                    sharingMode = SharingMode.READONLY,
                    campaignKeys = listOf(
                        "test-tenant-1:cled7a4u6000001758kw1qmgf"
                    )
                )
            )
            val report9 = reportRepository.save(
                reportPrototype.copy(
                    reference = "98wekd5bdg",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "New Report",
                    sharingMode = SharingMode.WRITE,
                    campaignKeys = listOf(
                        "test-tenant-1:cled7a4u6000001758kw1qmgf",
                        "test-tenant-1:cld63tq44000b0120cqwf3pmu",
                        "test-tenant-1:cld78m3p1000i0120k9q5q3bw"
                    )
                )
            )
            val report10 = reportRepository.save(
                reportPrototype.copy(
                    reference = "646mgyfwj0",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "Do nothing",
                    campaignKeys = listOf(
                        "test-tenant-1:cliosq48c00080j55ckax3j00"
                    )
                )
            )
            reportRepository.save(
                reportPrototype.copy(
                    reference = "32v8i26rgx",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "test campaignpatterns",
                    sharingMode = SharingMode.READONLY,
                    campaignNamesPatterns = listOf(
                        "Checkout stress test",
                        "Spike on Login"
                    )
                )
            )

            // Save related data components.
            val dataComponentsForReport8 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report8.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DATA_TABLE,
                        reportId = report8.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            val dataComponentsForReport10 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DATA_TABLE,
                        reportId = report10.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report10.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            val dataComponentsForReport6 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report6.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DATA_TABLE,
                        reportId = report6.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report6.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            val dataComponentsForReport5 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DATA_TABLE,
                        reportId = report5.id,
                        dataSeries = emptyList()
                    ),
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report5.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            val dataComponentsForReport7 = reportDataComponentRepository.saveAll(
                listOf(
                    ReportDataComponentEntity(
                        type = DataComponentType.DIAGRAM,
                        reportId = report7.id,
                        dataSeries = emptyList()
                    )
                )
            ).toList()
            assertThat(reportRepository.findAll().count()).isEqualTo(11)


            // when
            val result = reportRepository.findByIdIn(
                listOf(
                    report9.id,
                    report4.id,
                    report5.id,
                    report1.id,
                    report2.id,
                    report10.id,
                    report8.id,
                    report7.id,
                    report6.id,
                    report3.id
                )
            )
            // then
            assertThat(result.toList()).all {
                hasSize(10)
                containsOnly(
                    report9,
                    report4,
                    report5.copy(
                        dataComponents = dataComponentsForReport5
                    ),
                    report1,
                    report2,
                    report10.copy(
                        dataComponents = dataComponentsForReport10
                    ),
                    report8.copy(
                        dataComponents = dataComponentsForReport8
                    ),
                    report7.copy(
                        dataComponents = dataComponentsForReport7
                    ),
                    report6.copy(
                        dataComponents = dataComponentsForReport6
                    ),
                    report3
                )
            }
        }

    @Test
    fun `should fetch all reports and sort them by the sharing mode desc`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val reportWithWriteSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        val reportWithNoSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-2",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-2",
                sharingMode = SharingMode.NONE
            )
        )
        val reportWithReadSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-3",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-3"
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(3)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference, creator.username, Pageable.from(0, 3, Sort.of(Sort.Order.desc("sharingMode")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(3)
            containsExactly(reportWithWriteSharingMode, reportWithReadSharingMode, reportWithNoSharingMode)
        }
    }

    @Test
    fun `should fetch all reports that belong to user alone or sharing mode is not none`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))
            val report1 = reportRepository.save(
                reportPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id
                )
            )
            val reportWithWriteSharingMode = reportRepository.save(
                reportPrototype.copy(
                    reference = "report-ref-1",
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    displayName = "my-report-name-1",
                    sharingMode = SharingMode.WRITE,
                )
            )
            val reportWithNoSharingMode = reportRepository.save(
                reportPrototype.copy(
                    reference = "report-ref-2",
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    displayName = "my-report-name-2",
                    sharingMode = SharingMode.NONE
                )
            )
            val reportWithReadSharingMode = reportRepository.save(
                reportPrototype.copy(
                    reference = "report-ref-3",
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    displayName = "my-report-name-3"
                )
            )
            assertThat(reportRepository.findAll().count()).isEqualTo(4)

            // when + then
            assertThat(
                reportRepository.searchReports(
                    tenant.reference, creator.username, Pageable.from(0, 4, Sort.of(Sort.Order("displayName")))
                ).content.map { reportRepository.findById(it)!! }
            ).all {
                hasSize(3)
                containsExactly(report1, reportWithWriteSharingMode, reportWithReadSharingMode)
                doesNotContain(reportWithNoSharingMode)
            }
        }

    @Test
    fun `should fetch only reports belonging to only the specified tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val otherTenant = tenantRepository.save(tenantPrototype.copy(reference = "other-tenant"))
        val creator = userRepository.save(userPrototype.copy())
        val report1 = reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id
            )
        )
        val reportWithOtherTenant = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = otherTenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(2)

        // when + then
        assertThat(
            reportRepository.searchReports(
                otherTenant.reference, creator.username, Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(1)
            containsExactly(reportWithOtherTenant)
            doesNotContain(report1)
        }
    }

    @Test
    fun `should find all reports with filter on report display name`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id
            )
        )
        val anotherReport = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(2)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%e-1"),
                Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(1)
            containsExactly(anotherReport)
        }
    }

    @Test
    fun `should find all reports with filter on report display name with paging`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id
            )
        )
        val saved1 = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        val saved2 = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-2",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-2",
                sharingMode = SharingMode.WRITE,
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(3)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%Na_E-%"),
                Pageable.from(0, 1, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).containsExactly(saved1)
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%Na_E-%"),
                Pageable.from(1, 1, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).containsExactly(saved2)
    }

    @Test
    fun `should find all reports with filter on report display name with paging desc`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id
            )
        )
        val saved1 = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        val saved2 = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-2",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-2",
                sharingMode = SharingMode.WRITE,
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(3)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%Na_E-%"),
                Pageable.from(0, 1, Sort.of(Sort.Order.desc("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).containsExactly(saved2)
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%Na_E-%"),
                Pageable.from(1, 1, Sort.of(Sort.Order.desc("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).containsExactly(saved1)
    }

    @Test
    fun `should find all reports with filter on data series display name included in report`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val saved = reportRepository.save(
                reportPrototype.copy(
                    reference = "report-ref",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-report-name",
                )
            )
            val dataSeriesEntities = dataSeriesRepository.saveAll(
                dataComponents.map { reportDataComponentEntity ->
                    DataSeriesEntity(
                        reference = "series-ref_${reportDataComponentEntity.type}",
                        tenantId = tenant.id,
                        creatorId = creator.id,
                        displayName = "my-series-name_${reportDataComponentEntity.type}",
                        dataType = DataType.METERS,
                        valueName = "my-value",
                    )
                }
            ).toList()
            reportDataComponentRepository.saveAll(
                dataComponents.mapIndexed { index, reportDataComponentEntity ->
                    reportDataComponentEntity.copy(reportId = saved.id, dataSeries = listOf(dataSeriesEntities[index]))
                }
            ).toList()

            // when + then
            assertThat(
                reportRepository.searchReports(
                    tenant.reference,
                    creator.username,
                    listOf("%-series%"),
                    Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
                ).content.map { reportRepository.findById(it)!! }
            ).all {
                hasSize(1)
                index(0).prop(ReportEntity::id).isEqualTo(saved.id)
                index(0).prop(ReportEntity::dataComponents).all {
                    hasSize(2)
                    index(0).all {
                        prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DIAGRAM)
                    }
                    index(1).all {
                        prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DATA_TABLE)
                    }
                }
            }
        }

    @Test
    fun `should find only reports that match filter on data series display name`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val saved = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
                sharingMode = SharingMode.WRITE,
            )
        )
        val dataSeriesEntities = dataSeriesRepository.saveAll(
            dataComponents.map { reportDataComponentEntity ->
                DataSeriesEntity(
                    reference = "series-ref_${reportDataComponentEntity.type}",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-series-name_${reportDataComponentEntity.type}",
                    dataType = DataType.METERS,
                    valueName = "my-value",
                )
            }
        ).toList()
        reportDataComponentRepository.saveAll(
            dataComponents.mapIndexed { index, reportDataComponentEntity ->
                reportDataComponentEntity.copy(reportId = saved.id, dataSeries = listOf(dataSeriesEntities[index]))
            }
        ).toList()

        val saved2 = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-2",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-2",
                sharingMode = SharingMode.NONE
            )
        )
        val dataSeriesEntities2 = dataSeriesRepository.saveAll(
            dataComponents.map { reportDataComponentEntity ->
                DataSeriesEntity(
                    reference = "${reportDataComponentEntity.type}_series-ref",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "${reportDataComponentEntity.type}my-series",
                    dataType = DataType.METERS,
                    valueName = "my-value",
                )
            }
        ).toList()
        reportDataComponentRepository.saveAll(
            dataComponents.mapIndexed { index, reportDataComponentEntity ->
                reportDataComponentEntity.copy(reportId = saved2.id, dataSeries = listOf(dataSeriesEntities2[index]))
            }
        ).toList()

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%-series-%"),
                Pageable.from(0, 4, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(1)
            index(0).prop(ReportEntity::id).isEqualTo(saved.id)
            index(0).prop(ReportEntity::dataComponents).all {
                hasSize(2)
                index(0).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DIAGRAM)
                }
                index(1).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DATA_TABLE)
                }
            }
        }
    }

    @Test
    fun `should find only shared reports with filter on data series display name`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))
        val saved = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name",
                sharingMode = SharingMode.WRITE,
            )
        )
        val dataSeriesEntities = dataSeriesRepository.saveAll(
            dataComponents.map { reportDataComponentEntity ->
                DataSeriesEntity(
                    reference = "series-ref_${reportDataComponentEntity.type}",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-series-name_${reportDataComponentEntity.type}",
                    dataType = DataType.METERS,
                    valueName = "my-value",
                )
            }
        ).toList()
        reportDataComponentRepository.saveAll(
            dataComponents.mapIndexed { index, reportDataComponentEntity ->
                reportDataComponentEntity.copy(reportId = saved.id, dataSeries = listOf(dataSeriesEntities[index]))
            }
        ).toList()

        val saved2 = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-2",
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-report-name-2",
                sharingMode = SharingMode.NONE
            )
        )
        val dataSeriesEntities2 = dataSeriesRepository.saveAll(
            dataComponents.map { reportDataComponentEntity ->
                DataSeriesEntity(
                    reference = "${reportDataComponentEntity.type}_series-ref",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "${reportDataComponentEntity.type}my-series",
                    dataType = DataType.METERS,
                    valueName = "my-value",
                )
            }
        ).toList()
        reportDataComponentRepository.saveAll(
            dataComponents.mapIndexed { index, reportDataComponentEntity ->
                reportDataComponentEntity.copy(reportId = saved2.id, dataSeries = listOf(dataSeriesEntities2[index]))
            }
        ).toList()

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%-series%"),
                Pageable.from(0, 4, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(1)
            index(0).prop(ReportEntity::id).isEqualTo(saved.id)
            index(0).prop(ReportEntity::dataComponents).all {
                hasSize(2)
                index(0).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DIAGRAM)
                }
                index(1).all {
                    prop(ReportDataComponentEntity::type).isEqualTo(DataComponentType.DATA_TABLE)
                }
            }
        }
    }

    @Test
    fun `should find all reports with filter on report display name case insensitive`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id
            )
        )
        val anotherReport = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(2)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%NaME-1"),
                Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(1)
            containsExactly(anotherReport)
        }
    }

    @Test
    fun `should find all reports with filter on username name`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))
        reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id
            )
        )
        val anotherReport = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(2)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("_nother%"),
                Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(1)
            containsExactly(anotherReport)
        }
    }

    @Test
    fun `should find all reports with filter on creator display name`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator =
            userRepository.save(userPrototype.copy(username = "another-user", displayName = "unique-user"))
        reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id
            )
        )
        val anotherReport = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(2)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%que-u%"),
                Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).all {
            hasSize(1)
            containsExactly(anotherReport)
        }
    }

    @Test
    fun `should fetch nothing if filters don't match`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator =
            userRepository.save(userPrototype.copy(username = "another-user", displayName = "unique-user"))
        reportRepository.save(
            reportPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id
            )
        )
        reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(2)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference,
                creator.username,
                listOf("%good%"),
                Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
            ).content.map { reportRepository.findById(it)!! }
        ).isEmpty()
    }

    @Test
    fun `should fetch all reports with paging`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val reportWithWriteSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-1",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-1",
                sharingMode = SharingMode.WRITE,
            )
        )
        val reportWithNoSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-2",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-2",
                sharingMode = SharingMode.NONE
            )
        )
        val reportWithReadSharingMode = reportRepository.save(
            reportPrototype.copy(
                reference = "report-ref-3",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-report-name-3"
            )
        )
        assertThat(reportRepository.findAll().count()).isEqualTo(3)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference, creator.username, Pageable.from(0, 2, Sort.of(Sort.Order("sharingMode")))
            ).content.map { reportRepository.findById(it)!! }
        ).containsExactly(reportWithNoSharingMode, reportWithReadSharingMode)

        // when + then
        assertThat(
            reportRepository.searchReports(
                tenant.reference, creator.username, Pageable.from(1, 2, Sort.of(Sort.Order("sharingMode")))
            ).content.map { reportRepository.findById(it)!! }
        ).containsExactly(reportWithWriteSharingMode)
    }
}