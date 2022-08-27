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
import io.mockk.impl.annotations.RelaxedMockK
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
import io.qalipsis.core.head.model.DataSeriesFilter
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.model.converter.DataSeriesConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

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

    @RelaxedMockK
    private lateinit var dataSeriesConverter: DataSeriesConverter

    @Test
    internal fun `should create the data series with the default operation and no field name`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { dataSeriesRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference("my-tenant") } returns 123L
            coEvery { userRepository.findIdByUsername("my-user") } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            coEvery { dataProvider.createQuery("my-tenant", DataType.EVENTS, any()) } returns "the-query"

            val dataSeries = DataSeries(
                displayName = "the-name",
                dataType = DataType.EVENTS,
                color = "#ff761c",
                filters = setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B")),
                fieldName = null,
                aggregationOperation = null,
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
            }
            coVerify {
                dataProvider.createQuery("my-tenant", DataType.EVENTS, withArg {
                    assertThat(it).isDataClassEqualTo(
                        QueryDescription(
                            filters = listOf(QueryClause("field-1", QueryClauseOperator.IS_IN, "A,B")),
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
                    }
                })
            }
        }

    @Test
    internal fun `should create the data series with the specified operation and field name`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { dataSeriesRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference("my-tenant") } returns 123L
            coEvery { userRepository.findIdByUsername("my-user") } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            coEvery { dataProvider.createQuery("my-tenant", DataType.EVENTS, any()) } returns "the-query"
            val dataSeries = DataSeries(
                displayName = "the-name",
                dataType = DataType.EVENTS,
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
            }
            coVerify {
                dataProvider.createQuery("my-tenant", DataType.EVENTS, withArg {
                    assertThat(it).isDataClassEqualTo(
                        QueryDescription(
                            filters = listOf(QueryClause("field-1", QueryClauseOperator.IS_IN, "A,B")),
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
                    }
                })
            }
        }

    @Test
    internal fun `should not create the data series with the specified operation requires a field name, which is missing`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { dataSeriesRepository.save(any()) } returnsArgument 0
            coEvery { tenantRepository.findIdByReference("my-tenant") } returns 123L
            coEvery { userRepository.findIdByUsername("my-user") } returns 456L
            coEvery { idGenerator.short() } returns "the-reference"
            val dataSeries = DataSeries(
                displayName = "the-name",
                dataType = DataType.EVENTS,
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
            val result = dataSeriesService.get(username = "my-user", tenant = "my-tenant", reference = "my-data-series")

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("my-data-series")
                prop(DataSeries::creator).isEqualTo("the-creator")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
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
        val result = dataSeriesService.get(username = "the-creator", tenant = "my-tenant", reference = "my-data-series")

        // then
        assertThat(result).all {
            prop(DataSeries::reference).isEqualTo("my-data-series")
            prop(DataSeries::creator).isEqualTo("the-creator")
            prop(DataSeries::displayName).isEqualTo("the-name")
            prop(DataSeries::sharingMode).isEqualTo(SharingMode.NONE)
            prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
            prop(DataSeries::color).isEqualTo("#FF761C")
            prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B")))
            prop(DataSeries::fieldName).isEqualTo("the field")
            prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
            prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
            prop(DataSeries::displayFormat).isEqualTo("#0.000")
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
            dataSeriesService.get(username = "the-user", tenant = "my-tenant", reference = "my-data-series")
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should update the data series when shared in write and save if there are changes`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { dataProvider.createQuery("my-tenant", DataType.EVENTS, any()) } returns "the new query"
            coEvery { dataSeriesRepository.update(any()) } returnsArgument 0
            val entity = DataSeriesEntity(
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
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
                username = "my-user",
                tenant = "my-tenant",
                reference = "my-data-series",
                listOf(patch1, patch2)
            )

            // then
            coVerifyOnce {
                dataProvider.createQuery("my-tenant", DataType.EVENTS, withArg {
                    assertThat(it).isDataClassEqualTo(
                        QueryDescription(
                            filters = listOf(QueryClause("field-1", QueryClauseOperator.IS_IN, "A,B")),
                            fieldName = "the field",
                            aggregationOperation = QueryAggregationOperator.AVERAGE,
                            timeframeUnit = Duration.ofSeconds(2)
                        )
                    )
                })
            }

            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("my-data-series")
                prop(DataSeries::creator).isEqualTo("the-creator")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.WRITE)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(
                    setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B"))
                )
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
            }
            assertThat(entity.query).isEqualTo("the new query")
            coVerifyOrder {
                dataSeriesRepository.findByTenantAndReference(tenant = "my-tenant", reference = "my-data-series")
                userRepository.findUsernameById(3912L)
                patch1.apply(refEq(entity))
                patch2.apply(refEq(entity))
                dataSeriesRepository.update(refEq(entity))
            }
        }

    @Test
    internal fun `should update the data series when shared in write but not save if there is no change`() =
        testDispatcherProvider.runTest {
            // given
            val entity = DataSeriesEntity(
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
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
                username = "my-user",
                tenant = "my-tenant",
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
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(
                    setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B"))
                )
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
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
            coEvery { dataSeriesRepository.update(any()) } returnsArgument 0
            coEvery { dataProvider.createQuery("my-tenant", DataType.EVENTS, any()) } returns "the new query"
            val entity = DataSeriesEntity(
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
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
                username = "the-creator",
                tenant = "my-tenant",
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
            }
            coVerifyOrder {
                dataSeriesRepository.findByTenantAndReference(tenant = "my-tenant", reference = "my-data-series")
                userRepository.findUsernameById(3912L)
                patch1.apply(refEq(entity))
                patch2.apply(refEq(entity))
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
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
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
                username = "the-user",
                tenant = "my-tenant",
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
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
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
                username = "the-user",
                tenant = "my-tenant",
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
        dataSeriesService.delete(username = "my-user", tenant = "my-tenant", reference = "my-data-series")

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
        dataSeriesService.delete(username = "the-creator", tenant = "my-tenant", reference = "my-data-series")

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
            dataSeriesService.delete(username = "the-user", tenant = "my-tenant", reference = "my-data-series")
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
            dataSeriesService.delete(username = "the-user", tenant = "my-tenant", reference = "my-data-series")
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
            val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity>()
            val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("displayName")))
            val page = Page.of(listOf(dataSeriesEntity1, dataSeriesEntity2), pageable, 2)
            coEvery { dataSeriesRepository.searchDataSeries("my-tenant", "user", pageable) } returns page
            coEvery { dataSeriesConverter.convertToModel(any()) } returns dataSeries1 andThen dataSeries2

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
                dataSeriesConverter.convertToModel(refEq(dataSeriesEntity1))
                dataSeriesConverter.convertToModel(refEq(dataSeriesEntity2))
            }
            confirmVerified(dataSeriesRepository, dataSeriesConverter)
        }

    @Test
    internal fun `should return the searched data series from the repository with specified sorting and no filter`() =
        testDispatcherProvider.run {
            // given
            val dataSeries1 = relaxedMockk<DataSeries>()
            val dataSeries2 = relaxedMockk<DataSeries>()
            val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity>()
            val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("fieldName")))
            val page = Page.of(listOf(dataSeriesEntity1, dataSeriesEntity2), pageable, 2)
            coEvery { dataSeriesRepository.searchDataSeries("my-tenant", "user", pageable) } returns page
            coEvery { dataSeriesConverter.convertToModel(any()) } returns dataSeries1 andThen dataSeries2

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
                dataSeriesConverter.convertToModel(refEq(dataSeriesEntity1))
                dataSeriesConverter.convertToModel(refEq(dataSeriesEntity2))
            }
            confirmVerified(dataSeriesRepository, dataSeriesConverter)
        }

    @Test
    internal fun `should return the searched data series from the repository with specified filters and default sort`() =
        testDispatcherProvider.run {
            // given
            val filter1 = "%Un%u_%"
            val filter2 = "%u_Er%"
            val dataSeriesEntity1 = relaxedMockk<DataSeriesEntity>()
            val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity>()
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
            val dataSeries1 = relaxedMockk<DataSeries>()
            val dataSeries2 = relaxedMockk<DataSeries>()
            coEvery { dataSeriesConverter.convertToModel(any()) } returns dataSeries1 andThen dataSeries2

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
                dataSeriesConverter.convertToModel(refEq(dataSeriesEntity1))
                dataSeriesConverter.convertToModel(refEq(dataSeriesEntity2))
            }
            confirmVerified(dataSeriesRepository, dataSeriesConverter)
        }

    @Test
    internal fun `should return the searched data series from the repository with specified sorting and filters`() =
        testDispatcherProvider.run {
            // given
            val filter1 = "%F_oo%"
            val filter2 = "%Us_r%"
            val dataSeriesEntity2 = relaxedMockk<DataSeriesEntity>()
            val dataSeriesEntity3 = relaxedMockk<DataSeriesEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order("fieldName")))
            val page = Page.of(listOf(dataSeriesEntity2, dataSeriesEntity3), Pageable.from(0, 20), 2)
            coEvery {
                dataSeriesRepository.searchDataSeries(
                    "my-tenant",
                    "user",
                    listOf(filter1, filter2),
                    pageable
                )
            } returns page
            val dataSeries2 = relaxedMockk<DataSeries>()
            val dataSeries3 = relaxedMockk<DataSeries>()
            coEvery { dataSeriesConverter.convertToModel(any()) } returns dataSeries2 andThen dataSeries3

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
                    containsExactly(dataSeries2, dataSeries3)
                }
            }
            coVerifyOrder {
                dataSeriesRepository.searchDataSeries("my-tenant", "user", listOf(filter1, filter2), pageable)
                dataSeriesConverter.convertToModel(refEq(dataSeriesEntity2))
                dataSeriesConverter.convertToModel(refEq(dataSeriesEntity3))
            }
            confirmVerified(dataSeriesRepository, dataSeriesConverter)
        }
}