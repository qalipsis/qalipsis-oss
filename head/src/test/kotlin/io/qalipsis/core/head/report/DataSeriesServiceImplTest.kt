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
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClause
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.query.QueryDescription
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesCreationRequest
import io.qalipsis.core.head.model.DataSeriesFilter
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

@WithMockk
internal class DataSeriesServiceImplTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var dataSeriesRepository: DataSeriesRepository

    @MockK
    private lateinit var tenantRepository: TenantRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var idGenerator: IdGenerator

    @MockK
    private lateinit var dataProvider: DataProvider

    @InjectMockKs
    private lateinit var dataSeriesService: DataSeriesServiceImpl

    @Test
    internal fun `should create the data series with the default operation, no field name and color opacity of 5`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    any(),
                    any(),
                    any()
                )
            } returns false
            coEvery { dataSeriesRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference("my-tenant") } returns 123L
            coEvery { userRepository.findIdByUsername("my-user") } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            coEvery { dataProvider.createQuery("my-tenant", DataType.EVENTS, any()) } returns "the-query"

            val dataSeries = DataSeriesCreationRequest(
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                color = "#ff761c",
                filters = setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = null,
                aggregationOperation = null,
                timeframeUnit = Duration.ofSeconds(2),
                displayFormat = "#0.000",
                colorOpacity = 5
            )

            // when
            val result = dataSeriesService.create(creator = "my-user", tenant = "my-tenant", dataSeries = dataSeries)

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("the-reference")
                prop(DataSeries::creator).isEqualTo("my-user")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::valueName).isEqualTo("my-event")
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(
                    setOf(
                        DataSeriesFilter(
                            "field-1",
                            QueryClauseOperator.IS_IN,
                            "A,B"
                        )
                    )
                )
                prop(DataSeries::fieldName).isNull()
                prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.COUNT)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
                prop(DataSeries::colorOpacity).isEqualTo(5)
            }
            coVerify {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot("my-tenant", "the-name", -1)
                dataProvider.createQuery("my-tenant", DataType.EVENTS, withArg {
                    assertThat(it).isDataClassEqualTo(
                        QueryDescription(
                            filters = listOf(
                                QueryClause("tag.field-1", QueryClauseOperator.IS_IN, "A,B"),
                                QueryClause("name", QueryClauseOperator.IS, "my-event")
                            ),
                            fieldName = null,
                            aggregationOperation = QueryAggregationOperator.COUNT,
                            timeframeUnit = Duration.ofSeconds(2)
                        )
                    )
                })
                dataSeriesRepository.save(withArg {
                    assertThat(it).all {
                        prop(DataSeriesEntity::id).isEqualTo(-1)
                        prop(DataSeriesEntity::reference).isEqualTo("the-reference")
                        prop(DataSeriesEntity::tenantId).isEqualTo(123L)
                        prop(DataSeriesEntity::creatorId).isEqualTo(456L)
                        prop(DataSeriesEntity::displayName).isEqualTo("the-name")
                        prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.READONLY)
                        prop(DataSeriesEntity::dataType).isEqualTo(DataType.EVENTS)
                        prop(DataSeriesEntity::color).isEqualTo("#FF761C")
                        prop(DataSeriesEntity::filters).containsOnly(
                            DataSeriesFilterEntity(
                                "field-1",
                                QueryClauseOperator.IS_IN,
                                "A,B"
                            )
                        )
                        prop(DataSeriesEntity::fieldName).isNull()
                        prop(DataSeriesEntity::aggregationOperation).isEqualTo(QueryAggregationOperator.COUNT)
                        prop(DataSeriesEntity::timeframeUnitMs).isEqualTo(2_000L)
                        prop(DataSeriesEntity::displayFormat).isEqualTo("#0.000")
                        prop(DataSeriesEntity::query).isEqualTo("the-query")
                        prop(DataSeriesEntity::colorOpacity).isEqualTo(5)
                    }
                })
            }
        }

    @Test
    internal fun `should create the data series with the specified operation and field name`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    any(),
                    any(),
                    any()
                )
            } returns false
            coEvery { dataSeriesRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference("my-tenant") } returns 123L
            coEvery { userRepository.findIdByUsername("my-user") } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            coEvery { dataProvider.createQuery("my-tenant", DataType.EVENTS, any()) } returns "the-query"
            val dataSeries = DataSeriesCreationRequest(
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                color = "#ff761c",
                filters = setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = QueryAggregationOperator.AVERAGE,
                timeframeUnit = Duration.ofSeconds(2),
                displayFormat = "#0.000"
            )

            // when
            val result = dataSeriesService.create(creator = "my-user", tenant = "my-tenant", dataSeries = dataSeries)

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("the-reference")
                prop(DataSeries::creator).isEqualTo("my-user")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::valueName).isEqualTo("my-event")
                prop(DataSeries::valueName).isEqualTo("my-event")
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(
                    setOf(
                        DataSeriesFilter(
                            "field-1",
                            QueryClauseOperator.IS_IN,
                            "A,B"
                        )
                    )
                )
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
                prop(DataSeries::colorOpacity).isNull()
            }
            coVerify {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot("my-tenant", "the-name", -1)
                dataProvider.createQuery("my-tenant", DataType.EVENTS, withArg {
                    assertThat(it).isDataClassEqualTo(
                        QueryDescription(
                            filters = listOf(
                                QueryClause("tag.field-1", QueryClauseOperator.IS_IN, "A,B"),
                                QueryClause("name", QueryClauseOperator.IS, "my-event")
                            ),
                            fieldName = "the field",
                            aggregationOperation = QueryAggregationOperator.AVERAGE,
                            timeframeUnit = Duration.ofSeconds(2)
                        )
                    )
                })
                dataSeriesRepository.save(withArg {
                    assertThat(it).all {
                        prop(DataSeriesEntity::id).isEqualTo(-1)
                        prop(DataSeriesEntity::reference).isEqualTo("the-reference")
                        prop(DataSeriesEntity::tenantId).isEqualTo(123L)
                        prop(DataSeriesEntity::creatorId).isEqualTo(456L)
                        prop(DataSeriesEntity::displayName).isEqualTo("the-name")
                        prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.READONLY)
                        prop(DataSeriesEntity::dataType).isEqualTo(DataType.EVENTS)
                        prop(DataSeriesEntity::valueName).isEqualTo("my-event")
                        prop(DataSeriesEntity::color).isEqualTo("#FF761C")
                        prop(DataSeriesEntity::filters).containsOnly(
                            DataSeriesFilterEntity(
                                "field-1",
                                QueryClauseOperator.IS_IN,
                                "A,B"
                            )
                        )
                        prop(DataSeriesEntity::fieldName).isEqualTo("the field")
                        prop(DataSeriesEntity::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
                        prop(DataSeriesEntity::timeframeUnitMs).isEqualTo(2_000L)
                        prop(DataSeriesEntity::displayFormat).isEqualTo("#0.000")
                        prop(DataSeriesEntity::query).isEqualTo("the-query")
                        prop(DataSeriesEntity::colorOpacity).isNull()
                    }
                })
            }
        }

    @Test
    internal fun `should not create the data series with the specified operation requires a field name, which is missing`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    any(),
                    any(),
                    any()
                )
            } returns false
            coEvery { dataSeriesRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference("my-tenant") } returns 123L
            coEvery { userRepository.findIdByUsername("my-user") } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            val dataSeries = DataSeriesCreationRequest(
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                color = "#ff761c",
                filters = setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = null,
                aggregationOperation = QueryAggregationOperator.AVERAGE,
                timeframeUnit = Duration.ofSeconds(2),
                displayFormat = "#0.000"
            )

            // when
            assertThrows<IllegalArgumentException> {
                dataSeriesService.create(creator = "my-user", tenant = "my-tenant", dataSeries = dataSeries)
            }
        }

    @Test
    internal fun `should not create the data series with one with the same name exists in the tenant`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    any(),
                    any(),
                    any()
                )
            } returns true
            val dataSeries = DataSeriesCreationRequest(
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                color = "#ff761c",
                filters = setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = null,
                aggregationOperation = QueryAggregationOperator.AVERAGE,
                timeframeUnit = Duration.ofSeconds(2),
                displayFormat = "#0.000"
            )

            // when
            assertThrows<IllegalArgumentException> {
                dataSeriesService.create(creator = "my-user", tenant = "my-tenant", dataSeries = dataSeries)
            }
        }

    @Test
    internal fun `should get the data series when shared`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.findByTenantAndReference(
                    "my-tenant",
                    "my-data-series"
                )
            } returns DataSeriesEntity(
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                sharingMode = SharingMode.READONLY,
                color = "#FF761C",
                filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = QueryAggregationOperator.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

            // when
            val result = dataSeriesService.get(tenant = "my-tenant", username = "my-user", reference = "my-data-series")

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("my-data-series")
                prop(DataSeries::creator).isEqualTo("the-creator")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::valueName).isEqualTo("my-event")
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(
                    setOf(
                        DataSeriesFilter(
                            "field-1",
                            QueryClauseOperator.IS_IN,
                            "A,B"
                        )
                    )
                )
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
                prop(DataSeries::colorOpacity).isNull()
            }
        }

    @Test
    internal fun `should get the data series when owned if not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByTenantAndReference(
                "my-tenant",
                "my-data-series"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            sharingMode = SharingMode.NONE,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val result = dataSeriesService.get(tenant = "my-tenant", username = "the-creator", reference = "my-data-series")

        // then
        assertThat(result).all {
            prop(DataSeries::reference).isEqualTo("my-data-series")
            prop(DataSeries::creator).isEqualTo("the-creator")
            prop(DataSeries::displayName).isEqualTo("the-name")
            prop(DataSeries::sharingMode).isEqualTo(SharingMode.NONE)
            prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
            prop(DataSeries::valueName).isEqualTo("my-event")
            prop(DataSeries::color).isEqualTo("#FF761C")
            prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B")))
            prop(DataSeries::fieldName).isEqualTo("the field")
            prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
            prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
            prop(DataSeries::displayFormat).isEqualTo("#0.000")
            prop(DataSeries::colorOpacity).isNull()
        }
    }

    @Test
    internal fun `should not get the data series when not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByTenantAndReference(
                "my-tenant",
                "my-data-series"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            sharingMode = SharingMode.NONE,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            dataSeriesService.get(tenant = "my-tenant", username = "the-user", reference = "my-data-series")
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should update the data series when shared in write and save if there are changes`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    any(),
                    any(),
                    any()
                )
            } returns false
            coEvery { dataProvider.createQuery("my-tenant", DataType.EVENTS, any()) } returns "the new query"
            coEvery { dataSeriesRepository.update(any()) } returnsArgument 0
            val entity = DataSeriesEntity(
                id = 6432,
                version = Instant.now(),
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                sharingMode = SharingMode.WRITE,
                color = "#FF761C",
                filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = QueryAggregationOperator.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery {
                dataSeriesRepository.findByTenantAndReference(
                    "my-tenant",
                    "my-data-series"
                )
            } returns entity
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"
            val patch1 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns true }
            val patch2 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns false }

            // when
            val result = dataSeriesService.update(
                tenant = "my-tenant",
                username = "my-user",
                reference = "my-data-series",
                listOf(patch1, patch2)
            )

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("my-data-series")
                prop(DataSeries::creator).isEqualTo("the-creator")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.WRITE)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::valueName).isEqualTo("my-event")
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(
                    setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B"))
                )
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
                prop(DataSeries::colorOpacity).isNull()
            }
            assertThat(entity.query).isEqualTo("the new query")
            coVerifyOrder {
                dataSeriesRepository.findByTenantAndReference(tenant = "my-tenant", reference = "my-data-series")
                userRepository.findUsernameById(3912L)
                patch1.apply(refEq(entity))
                patch2.apply(refEq(entity))
                dataProvider.createQuery("my-tenant", DataType.EVENTS, withArg {
                    assertThat(it).isDataClassEqualTo(
                        QueryDescription(
                            filters = listOf(
                                QueryClause("tag.field-1", QueryClauseOperator.IS_IN, "A,B"),
                                QueryClause("name", QueryClauseOperator.IS, "my-event")
                            ),
                            fieldName = "the field",
                            aggregationOperation = QueryAggregationOperator.AVERAGE,
                            timeframeUnit = Duration.ofSeconds(2)
                        )
                    )
                })
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot("my-tenant", "the-name", 6432)
                dataSeriesRepository.update(refEq(entity))
            }
        }

    @Test
    internal fun `should update the data series when shared in write but not save if there is no change`() =
        testDispatcherProvider.runTest {
            // given
            val entity = DataSeriesEntity(
                id = 6432,
                version = Instant.now(),
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                sharingMode = SharingMode.WRITE,
                color = "#FF761C",
                filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = QueryAggregationOperator.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery {
                dataSeriesRepository.findByTenantAndReference(
                    "my-tenant",
                    "my-data-series"
                )
            } returns entity
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"
            val patch1 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns false }
            val patch2 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns false }

            // when
            val result = dataSeriesService.update(
                tenant = "my-tenant",
                username = "my-user",
                reference = "my-data-series",
                listOf(patch1, patch2)
            )

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("my-data-series")
                prop(DataSeries::creator).isEqualTo("the-creator")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.WRITE)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::valueName).isEqualTo("my-event")
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(
                    setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B"))
                )
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
                prop(DataSeries::colorOpacity).isNull()
            }
            coVerifyOrder {
                dataSeriesRepository.findByTenantAndReference(tenant = "my-tenant", reference = "my-data-series")
                userRepository.findUsernameById(3912L)
                patch1.apply(refEq(entity))
                patch2.apply(refEq(entity))
            }
            coVerifyNever { dataSeriesRepository.update(any()) }
            confirmVerified(dataProvider)
        }

    @Test
    internal fun `should update the data series when owned if not shared`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    any(),
                    any(),
                    any()
                )
            } returns false
            coEvery { dataSeriesRepository.update(any()) } returnsArgument 0
            coEvery { dataProvider.createQuery("my-tenant", DataType.EVENTS, any()) } returns "the new query"
            val entity = DataSeriesEntity(
                id = 6432,
                version = Instant.now(),
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                sharingMode = SharingMode.NONE,
                color = "#FF761C",
                filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = QueryAggregationOperator.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery {
                dataSeriesRepository.findByTenantAndReference(
                    "my-tenant",
                    "my-data-series"
                )
            } returns entity
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"
            val patch1 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns true }
            val patch2 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns false }

            // when
            val result = dataSeriesService.update(
                tenant = "my-tenant",
                username = "the-creator",
                reference = "my-data-series",
                listOf(patch1, patch2)
            )

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("my-data-series")
                prop(DataSeries::creator).isEqualTo("the-creator")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.NONE)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::valueName).isEqualTo("my-event")
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(
                    setOf(
                        DataSeriesFilter(
                            "field-1",
                            QueryClauseOperator.IS_IN,
                            "A,B"
                        )
                    )
                )
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
                prop(DataSeries::colorOpacity).isNull()
            }
            coVerifyOrder {
                dataSeriesRepository.findByTenantAndReference(tenant = "my-tenant", reference = "my-data-series")
                userRepository.findUsernameById(3912L)
                patch1.apply(refEq(entity))
                patch2.apply(refEq(entity))
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot("my-tenant", "the-name", 6432)
                dataSeriesRepository.update(refEq(entity))
            }
        }

    @Test
    internal fun `should not update the data series when shared in read`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByTenantAndReference(
                "my-tenant",
                "my-data-series"
            )
        } returns DataSeriesEntity(
            id = 6432,
            version = Instant.now(),
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            sharingMode = SharingMode.READONLY,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            dataSeriesService.update(
                tenant = "my-tenant",
                username = "the-user",
                reference = "my-data-series",
                emptyList()
            )
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should not update the data series when not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByTenantAndReference(
                "my-tenant",
                "my-data-series"
            )
        } returns DataSeriesEntity(
            id = 6432,
            version = Instant.now(),
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            sharingMode = SharingMode.NONE,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            dataSeriesService.update(
                tenant = "my-tenant",
                username = "the-user",
                reference = "my-data-series",
                emptyList()
            )
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should not update the data series when another one exists with the same name in the tenant`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    any(),
                    any(),
                    any()
                )
            } returns true
            coEvery {
                dataSeriesRepository.findByTenantAndReference(
                    "my-tenant",
                    "my-data-series"
                )
            } returns DataSeriesEntity(
                id = 6432,
                version = Instant.now(),
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
                valueName = "my-event",
                sharingMode = SharingMode.READONLY,
                color = "#FF761C",
                filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = QueryAggregationOperator.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

            // when
            val exception = assertThrows<HttpStatusException> {
                dataSeriesService.update(
                    tenant = "my-tenant",
                    username = "the-user",
                    reference = "my-data-series",
                    emptyList()
                )
            }

            // then
            assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
        }

    @Test
    internal fun `should delete the data series when shared in write`() = testDispatcherProvider.runTest {
        // given
        coEvery { dataSeriesRepository.delete(any()) } returns 1
        val entity = mockk<DataSeriesEntity> {
            every { creatorId } returns 3912L
            every { sharingMode } returns SharingMode.WRITE
        }
        coEvery {
            dataSeriesRepository.findByTenantAndReference(
                "my-tenant",
                "my-data-series"
            )
        } returns entity

        // when
        dataSeriesService.delete(tenant = "my-tenant", username = "my-user", reference = "my-data-series")

        // then
        coVerifyOrder {
            dataSeriesRepository.findByTenantAndReference(tenant = "my-tenant", reference = "my-data-series")
            dataSeriesRepository.delete(refEq(entity))
        }
    }

    @Test
    internal fun `should delete the data series when owned if not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery { dataSeriesRepository.delete(any()) } returns 1
        val entity = mockk<DataSeriesEntity> {
            every { creatorId } returns 3912L
            every { sharingMode } returns SharingMode.NONE
        }
        coEvery {
            dataSeriesRepository.findByTenantAndReference(
                "my-tenant",
                "my-data-series"
            )
        } returns entity
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        dataSeriesService.delete(tenant = "my-tenant", username = "the-creator", reference = "my-data-series")

        // then
        coVerifyOrder {
            dataSeriesRepository.findByTenantAndReference(tenant = "my-tenant", reference = "my-data-series")
            userRepository.findUsernameById(3912L)
            dataSeriesRepository.delete(refEq(entity))
        }
    }

    @Test
    internal fun `should not delete the data series when shared in read`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByTenantAndReference(
                "my-tenant",
                "my-data-series"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            sharingMode = SharingMode.READONLY,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            dataSeriesService.delete(tenant = "my-tenant", username = "the-user", reference = "my-data-series")
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should not delete the data series when not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByTenantAndReference(
                "my-tenant",
                "my-data-series"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "my-event",
            sharingMode = SharingMode.NONE,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", QueryClauseOperator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            dataSeriesService.delete(tenant = "my-tenant", username = "the-user", reference = "my-data-series")
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should return the searched data series from the repository with default sorting and no filter`() =
        testDispatcherProvider.run {
            // given
            val dataSeries1 = relaxedMockk<DataSeries>()
            val dataSeries2 = relaxedMockk<DataSeries>()
            val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity> {
                every { creatorId } returns 12
                every { toModel("User 1") } returns dataSeries1
            }
            val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity> {
                every { creatorId } returns 34
                every { toModel("User 2") } returns dataSeries2
            }
            coEvery { userRepository.findAllByIdIn(setOf(12L, 34L)) } returns listOf(
                mockk { every { id } returns 12L; every { displayName } returns "User 1" },
                mockk { every { id } returns 34L; every { displayName } returns "User 2" }
            )
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("displayName")))
            val page = Page.of(listOf(dataSeriesEntity1, dataSeriesEntity2), pageable, 2)
            coEvery { dataSeriesRepository.searchDataSeries("my-tenant", "user", pageable) } returns page

            // when
            val result = dataSeriesService.searchDataSeries("my-tenant", "user", emptyList(), null, 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<DataSeries>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<DataSeries>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<DataSeries>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<DataSeries>::elements).all {
                    hasSize(2)
                    containsExactly(dataSeries1, dataSeries2)
                }
            }
            coVerifyOrder {
                dataSeriesRepository.searchDataSeries("my-tenant", "user", pageable)
            }
            confirmVerified(dataSeriesRepository)
        }

    @Test
    internal fun `should return the searched data series from the repository with specified sorting and no filter`() =
        testDispatcherProvider.run {
            // given
            val dataSeries1 = relaxedMockk<DataSeries>()
            val dataSeries2 = relaxedMockk<DataSeries>()
            val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity> {
                every { creatorId } returns 12
                every { toModel("User 1") } returns dataSeries1
            }
            val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity> {
                every { creatorId } returns 34
                every { toModel("User 2") } returns dataSeries2
            }
            coEvery { userRepository.findAllByIdIn(setOf(12L, 34L)) } returns listOf(
                mockk { every { id } returns 12L; every { displayName } returns "User 1" },
                mockk { every { id } returns 34L; every { displayName } returns "User 2" }
            )
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("fieldName", true)))
            val page = Page.of(listOf(dataSeriesEntity1, dataSeriesEntity2), pageable, 2)
            coEvery { dataSeriesRepository.searchDataSeries("my-tenant", "user", pageable) } returns page

            // when
            val result = dataSeriesService.searchDataSeries("my-tenant", "user", emptyList(), "fieldName", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<DataSeries>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<DataSeries>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<DataSeries>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<DataSeries>::elements).all {
                    hasSize(2)
                    containsExactly(dataSeries1, dataSeries2)
                }
            }
            coVerifyOrder {
                dataSeriesRepository.searchDataSeries("my-tenant", "user", pageable)
            }
            confirmVerified(dataSeriesRepository)
        }

    @Test
    internal fun `should return the searched data series from the repository with specified filters and default sort`() =
        testDispatcherProvider.run {
            // given
            val filter1 = "%Un%u_%"
            val filter2 = "%u_Er%"
            val dataSeries1 = relaxedMockk<DataSeries>()
            val dataSeries2 = relaxedMockk<DataSeries>()
            val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity> {
                every { creatorId } returns 12
                every { toModel("User 1") } returns dataSeries1
            }
            val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity> {
                every { creatorId } returns 34
                every { toModel("User 2") } returns dataSeries2
            }
            coEvery { userRepository.findAllByIdIn(setOf(12L, 34L)) } returns listOf(
                mockk { every { id } returns 12L; every { displayName } returns "User 1" },
                mockk { every { id } returns 34L; every { displayName } returns "User 2" }
            )
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("displayName")))
            val page = Page.of(listOf(dataSeriesEntity1, dataSeriesEntity2), Pageable.from(0, 20), 2)
            coEvery {
                dataSeriesRepository.searchDataSeries(
                    "my-tenant",
                    "user",
                    listOf(filter1, filter2),
                    pageable
                )
            } returns page

            // when
            val result =
                dataSeriesService.searchDataSeries("my-tenant", "user", listOf("Un*u_", "u_Er"), null, 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<DataSeries>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<DataSeries>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<DataSeries>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<DataSeries>::elements).all {
                    hasSize(2)
                    containsExactly(dataSeries1, dataSeries2)
                }
            }
            coVerifyOrder {
                dataSeriesRepository.searchDataSeries("my-tenant", "user", listOf(filter1, filter2), pageable)
            }
            confirmVerified(dataSeriesRepository)
        }

    @Test
    internal fun `should return the searched data series from the repository with specified sorting and filters`() =
        testDispatcherProvider.run {
            // given
            val filter1 = "%F_oo%"
            val filter2 = "%Us_r%"
            val dataSeries1 = relaxedMockk<DataSeries>()
            val dataSeries2 = relaxedMockk<DataSeries>()
            val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity> {
                every { creatorId } returns 12
                every { toModel("User 1") } returns dataSeries1
            }
            val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity> {
                every { creatorId } returns 34
                every { toModel("User 2") } returns dataSeries2
            }
            coEvery { userRepository.findAllByIdIn(setOf(12L, 34L)) } returns listOf(
                mockk { every { id } returns 12L; every { displayName } returns "User 1" },
                mockk { every { id } returns 34L; every { displayName } returns "User 2" }
            )
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("fieldName", true)))
            val page = Page.of(listOf(dataSeriesEntity1, dataSeriesEntity2), Pageable.from(0, 20), 2)
            coEvery {
                dataSeriesRepository.searchDataSeries(
                    "my-tenant",
                    "user",
                    listOf(filter1, filter2),
                    pageable
                )
            } returns page

            // when
            val result =
                dataSeriesService.searchDataSeries("my-tenant", "user", listOf("F_oo", "Us_r"), "fieldName", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<DataSeries>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<DataSeries>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<DataSeries>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<DataSeries>::elements).all {
                    hasSize(2)
                    containsExactly(dataSeries1, dataSeries2)
                }
            }
            coVerifyOrder {
                dataSeriesRepository.searchDataSeries("my-tenant", "user", listOf(filter1, filter2), pageable)
            }
            confirmVerified(dataSeriesRepository)
        }
}