package io.qalipsis.core.head.report

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.head.jdbc.entity.AggregationOperation
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.entity.DataType
import io.qalipsis.core.head.jdbc.entity.Operator
import io.qalipsis.core.head.jdbc.entity.SharingMode
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesFilter
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
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

    @InjectMockKs
    private lateinit var service: DataSeriesServiceImpl

    @Test
    internal fun `should create the data series with the default operation and no field name`() =
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
                filters = setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")),
                fieldName = null,
                aggregationOperation = null,
                timeframeUnit = Duration.ofSeconds(2),
                displayFormat = "#0.000"
            )

            // when
            val result = service.create(creator = "my-user", tenant = "my-tenant", dataSeries = dataSeries)

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("the-reference")
                prop(DataSeries::creator).isEqualTo("my-user")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")))
                prop(DataSeries::fieldName).isNull()
                prop(DataSeries::aggregationOperation).isEqualTo(AggregationOperation.COUNT)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
            }
            coVerify {
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
                                Operator.IS_IN,
                                "A,B"
                            )
                        )
                        prop(DataSeriesEntity::fieldName).isNull()
                        prop(DataSeriesEntity::aggregationOperation).isEqualTo(AggregationOperation.COUNT)
                        prop(DataSeriesEntity::timeframeUnitMs).isEqualTo(2_000L)
                        prop(DataSeriesEntity::displayFormat).isEqualTo("#0.000")
                        prop(DataSeriesEntity::query).isNull()
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
            val dataSeries = DataSeries(
                displayName = "the-name",
                dataType = DataType.EVENTS,
                color = "#ff761c",
                filters = setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = AggregationOperation.AVERAGE,
                timeframeUnit = Duration.ofSeconds(2),
                displayFormat = "#0.000"
            )

            // when
            val result = service.create(creator = "my-user", tenant = "my-tenant", dataSeries = dataSeries)

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("the-reference")
                prop(DataSeries::creator).isEqualTo("my-user")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")))
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(AggregationOperation.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
            }
            coVerify {
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
                                Operator.IS_IN,
                                "A,B"
                            )
                        )
                        prop(DataSeriesEntity::fieldName).isEqualTo("the field")
                        prop(DataSeriesEntity::aggregationOperation).isEqualTo(AggregationOperation.AVERAGE)
                        prop(DataSeriesEntity::timeframeUnitMs).isEqualTo(2_000L)
                        prop(DataSeriesEntity::displayFormat).isEqualTo("#0.000")
                        prop(DataSeriesEntity::query).isNull()
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
                filters = setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")),
                fieldName = null,
                aggregationOperation = AggregationOperation.AVERAGE,
                timeframeUnit = Duration.ofSeconds(2),
                displayFormat = "#0.000"
            )

            // when
            assertThrows<IllegalArgumentException> {
                service.create(creator = "my-user", tenant = "my-tenant", dataSeries = dataSeries)
            }
        }

    @Test
    internal fun `should get the data series when shared`() =
        testDispatcherProvider.runTest {
            // given
            coEvery {
                dataSeriesRepository.findByReferenceAndTenant(
                    "my-data-series",
                    "my-tenant"
                )
            } returns DataSeriesEntity(
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
                sharingMode = SharingMode.READONLY,
                color = "#FF761C",
                filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = AggregationOperation.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

            // when
            val result = service.get(username = "my-user", tenant = "my-tenant", reference = "my-data-series")

            // then
            assertThat(result).all {
                prop(DataSeries::reference).isEqualTo("my-data-series")
                prop(DataSeries::creator).isEqualTo("the-creator")
                prop(DataSeries::displayName).isEqualTo("the-name")
                prop(DataSeries::sharingMode).isEqualTo(SharingMode.READONLY)
                prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
                prop(DataSeries::color).isEqualTo("#FF761C")
                prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")))
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(AggregationOperation.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
            }
        }

    @Test
    internal fun `should get the data series when owned if not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByReferenceAndTenant(
                "my-data-series",
                "my-tenant"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            sharingMode = SharingMode.NONE,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = AggregationOperation.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val result = service.get(username = "the-creator", tenant = "my-tenant", reference = "my-data-series")

        // then
        assertThat(result).all {
            prop(DataSeries::reference).isEqualTo("my-data-series")
            prop(DataSeries::creator).isEqualTo("the-creator")
            prop(DataSeries::displayName).isEqualTo("the-name")
            prop(DataSeries::sharingMode).isEqualTo(SharingMode.NONE)
            prop(DataSeries::dataType).isEqualTo(DataType.EVENTS)
            prop(DataSeries::color).isEqualTo("#FF761C")
            prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")))
            prop(DataSeries::fieldName).isEqualTo("the field")
            prop(DataSeries::aggregationOperation).isEqualTo(AggregationOperation.AVERAGE)
            prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
            prop(DataSeries::displayFormat).isEqualTo("#0.000")
        }
    }

    @Test
    internal fun `should not get the data series when not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByReferenceAndTenant(
                "my-data-series",
                "my-tenant"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            sharingMode = SharingMode.NONE,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = AggregationOperation.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            service.get(username = "the-user", tenant = "my-tenant", reference = "my-data-series")
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should update the data series when shared in write and save if there are changes`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { dataSeriesRepository.update(any()) } returnsArgument 0
            val entity = DataSeriesEntity(
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
                sharingMode = SharingMode.WRITE,
                color = "#FF761C",
                filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = AggregationOperation.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery {
                dataSeriesRepository.findByReferenceAndTenant(
                    "my-data-series",
                    "my-tenant"
                )
            } returns entity
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"
            val patch1 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns true }
            val patch2 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns false }

            // when
            val result = service.update(
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
                prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")))
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(AggregationOperation.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
            }
            coVerifyOrder {
                dataSeriesRepository.findByReferenceAndTenant(reference = "my-data-series", tenant = "my-tenant")
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
                filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = AggregationOperation.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery {
                dataSeriesRepository.findByReferenceAndTenant(
                    "my-data-series",
                    "my-tenant"
                )
            } returns entity
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"
            val patch1 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns false }
            val patch2 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns false }

            // when
            val result = service.update(
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
                prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")))
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(AggregationOperation.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
            }
            coVerifyOrder {
                dataSeriesRepository.findByReferenceAndTenant(reference = "my-data-series", tenant = "my-tenant")
                userRepository.findUsernameById(3912L)
                patch1.apply(refEq(entity))
                patch2.apply(refEq(entity))
            }
            coVerifyNever { dataSeriesRepository.update(any()) }
        }

    @Test
    internal fun `should update the data series when owned if not shared`() =
        testDispatcherProvider.runTest {
            // given
            coEvery { dataSeriesRepository.update(any()) } returnsArgument 0
            val entity = DataSeriesEntity(
                reference = "my-data-series",
                tenantId = -1,
                creatorId = 3912L,
                displayName = "the-name",
                dataType = DataType.EVENTS,
                sharingMode = SharingMode.NONE,
                color = "#FF761C",
                filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
                fieldName = "the field",
                aggregationOperation = AggregationOperation.AVERAGE,
                timeframeUnitMs = 2_000,
                displayFormat = "#0.000",
                query = "the query"
            )
            coEvery {
                dataSeriesRepository.findByReferenceAndTenant(
                    "my-data-series",
                    "my-tenant"
                )
            } returns entity
            coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"
            val patch1 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns true }
            val patch2 = mockk<DataSeriesPatch> { every { apply(any<DataSeriesEntity>()) } returns false }

            // when
            val result = service.update(
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
                prop(DataSeries::filters).isEqualTo(setOf(DataSeriesFilter("field-1", Operator.IS_IN, "A,B")))
                prop(DataSeries::fieldName).isEqualTo("the field")
                prop(DataSeries::aggregationOperation).isEqualTo(AggregationOperation.AVERAGE)
                prop(DataSeries::timeframeUnit).isEqualTo(Duration.ofSeconds(2))
                prop(DataSeries::displayFormat).isEqualTo("#0.000")
            }
            coVerifyOrder {
                dataSeriesRepository.findByReferenceAndTenant(reference = "my-data-series", tenant = "my-tenant")
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
            dataSeriesRepository.findByReferenceAndTenant(
                "my-data-series",
                "my-tenant"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            sharingMode = SharingMode.READONLY,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = AggregationOperation.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            service.update(username = "the-user", tenant = "my-tenant", reference = "my-data-series", emptyList())
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should not update the data series when not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByReferenceAndTenant(
                "my-data-series",
                "my-tenant"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            sharingMode = SharingMode.NONE,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = AggregationOperation.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            service.update(username = "the-user", tenant = "my-tenant", reference = "my-data-series", emptyList())
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
            dataSeriesRepository.findByReferenceAndTenant(
                "my-data-series",
                "my-tenant"
            )
        } returns entity

        // when
        service.delete(username = "my-user", tenant = "my-tenant", reference = "my-data-series")

        // then
        coVerifyOrder {
            dataSeriesRepository.findByReferenceAndTenant(reference = "my-data-series", tenant = "my-tenant")
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
            dataSeriesRepository.findByReferenceAndTenant(
                "my-data-series",
                "my-tenant"
            )
        } returns entity
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        service.delete(username = "the-creator", tenant = "my-tenant", reference = "my-data-series")

        // then
        coVerifyOrder {
            dataSeriesRepository.findByReferenceAndTenant(reference = "my-data-series", tenant = "my-tenant")
            userRepository.findUsernameById(3912L)
            dataSeriesRepository.delete(refEq(entity))
        }
    }

    @Test
    internal fun `should not delete the data series when shared in read`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByReferenceAndTenant(
                "my-data-series",
                "my-tenant"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            sharingMode = SharingMode.READONLY,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = AggregationOperation.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            service.delete(username = "the-user", tenant = "my-tenant", reference = "my-data-series")
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    internal fun `should not delete the data series when not shared`() = testDispatcherProvider.runTest {
        // given
        coEvery {
            dataSeriesRepository.findByReferenceAndTenant(
                "my-data-series",
                "my-tenant"
            )
        } returns DataSeriesEntity(
            reference = "my-data-series",
            tenantId = -1,
            creatorId = 3912L,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            sharingMode = SharingMode.NONE,
            color = "#FF761C",
            filters = setOf(DataSeriesFilterEntity("field-1", Operator.IS_IN, "A,B")),
            fieldName = "the field",
            aggregationOperation = AggregationOperation.AVERAGE,
            timeframeUnitMs = 2_000,
            displayFormat = "#0.000",
            query = "the query"
        )
        coEvery { userRepository.findUsernameById(3912L) } returns "the-creator"

        // when
        val exception = assertThrows<HttpStatusException> {
            service.delete(username = "the-user", tenant = "my-tenant", reference = "my-data-series")
        }

        // then
        assertThat(exception.status).isEqualTo(HttpStatus.FORBIDDEN)
    }
}