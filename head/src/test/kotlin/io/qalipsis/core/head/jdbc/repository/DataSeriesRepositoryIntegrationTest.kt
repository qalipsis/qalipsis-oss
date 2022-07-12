package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.DataAccessException
import io.qalipsis.api.report.query.QueryAggregationOperator
import io.qalipsis.api.report.query.QueryClauseOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.entity.UserEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

internal class DataSeriesRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var dataSeriesRepository: DataSeriesRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var userRepository: UserRepository

    private val tenantPrototype = TenantEntity(
        reference = "my-tenant",
        displayName = "test-tenant"
    )

    private val userPrototype = UserEntity(username = "my-user", displayName = "User for test")

    private val dataSeriesPrototype =
        DataSeriesEntity(
            reference = "my-series",
            tenantId = -1,
            creatorId = -1,
            displayName = "my-name",
            dataType = DataType.METERS,
            color = "#FFFFFF",
            filters = setOf(DataSeriesFilterEntity("minionsCount", QueryClauseOperator.IS, "1000")),
            timeframeUnitMs = 10_000L,
            fieldName = "my-field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            displayFormat = "#000.000",
            query = "This is the query"
        )

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        dataSeriesRepository.deleteAll()
        tenantRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `should save a minimal data series`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val saved = dataSeriesRepository.save(
            DataSeriesEntity(
                reference = "my-series",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-name",
                dataType = DataType.METERS
            )
        )

        // when
        val fetched = dataSeriesRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(DataSeriesEntity::id).isEqualTo(saved.id)
            prop(DataSeriesEntity::reference).isEqualTo("my-series")
            prop(DataSeriesEntity::tenantId).isEqualTo(tenant.id)
            prop(DataSeriesEntity::creatorId).isEqualTo(creator.id)
            prop(DataSeriesEntity::displayName).isEqualTo("my-name")
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

    @Test
    fun `should not save two data-series with same name in the tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        dataSeriesRepository.save(
            DataSeriesEntity(
                reference = "my-series",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-name",
                dataType = DataType.METERS
            )
        )

        // when
        assertThrows<DataAccessException> {
            dataSeriesRepository.save(
                DataSeriesEntity(
                    reference = "other-series",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name",
                    dataType = DataType.METERS
                )
            )
        }

        // then
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(1)
    }

    @Test
    fun `should not save two data-series with same reference in the tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        dataSeriesRepository.save(
            DataSeriesEntity(
                reference = "my-series",
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "my-name",
                dataType = DataType.METERS
            )
        )

        // when
        assertThrows<DataAccessException> {
            dataSeriesRepository.save(
                DataSeriesEntity(
                    reference = "my-series",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "other-name",
                    dataType = DataType.METERS
                )
            )
        }

        // then
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(1)
    }

    @Test
    fun `should save two data-series with same reference and name in different tenants`() = testDispatcherProvider.run {
        // given
        val tenant1 = tenantRepository.save(tenantPrototype.copy())
        val tenant2 = tenantRepository.save(tenantPrototype.copy(reference = "other-tenant"))
        val creator = userRepository.save(userPrototype.copy())
        dataSeriesRepository.save(
            DataSeriesEntity(
                reference = "my-series",
                tenantId = tenant1.id,
                creatorId = creator.id,
                displayName = "my-name",
                dataType = DataType.METERS
            )
        )

        // when
        assertDoesNotThrow {
            dataSeriesRepository.save(
                DataSeriesEntity(
                    reference = "my-series",
                    tenantId = tenant2.id,
                    creatorId = creator.id,
                    displayName = "my-name",
                    dataType = DataType.METERS
                )
            )
        }

        // then
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)
    }

    @Test
    fun `should save then update and fetch by reference and tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val saved = dataSeriesRepository.save(dataSeriesPrototype.copy(tenantId = tenant.id, creatorId = creator.id))

        // when
        var fetched = dataSeriesRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(DataSeriesEntity::id).isEqualTo(saved.id)
            prop(DataSeriesEntity::reference).isEqualTo("my-series")
            prop(DataSeriesEntity::tenantId).isEqualTo(tenant.id)
            prop(DataSeriesEntity::creatorId).isEqualTo(creator.id)
            prop(DataSeriesEntity::displayName).isEqualTo("my-name")
            prop(DataSeriesEntity::dataType).isEqualTo(DataType.METERS)
            prop(DataSeriesEntity::filters).containsOnly(
                DataSeriesFilterEntity(
                    "minionsCount",
                    QueryClauseOperator.IS,
                    "1000"
                )
            )
            prop(DataSeriesEntity::color).isEqualTo("#FFFFFF")
            prop(DataSeriesEntity::fieldName).isEqualTo("my-field")
            prop(DataSeriesEntity::aggregationOperation).isEqualTo(QueryAggregationOperator.AVERAGE)
            prop(DataSeriesEntity::timeframeUnitMs).isEqualTo(10_000L)
            prop(DataSeriesEntity::displayFormat).isEqualTo("#000.000")
            prop(DataSeriesEntity::query).isEqualTo("This is the query")
        }

        // when
        val updated = fetched!!.copy(
            displayName = "my other name",
            color = "#000000",
            filters = setOf(DataSeriesFilterEntity("campaign", QueryClauseOperator.IS_NOT, "AAA")),
            timeframeUnitMs = Duration.ofMinutes(4).toMillis(),
            fieldName = "my-other-field",
            aggregationOperation = QueryAggregationOperator.MAX,
            displayFormat = "##0.0",
            query = "This is the other query"
        )
        val beforeUpdate = Instant.now()
        dataSeriesRepository.update(updated)
        fetched = dataSeriesRepository.findByReferenceAndTenant(reference = "my-series", tenant = "my-tenant")

        // then
        assertThat(fetched).isNotNull().all {
            prop(DataSeriesEntity::reference).isEqualTo("my-series")
            prop(DataSeriesEntity::version).isGreaterThanOrEqualTo(beforeUpdate)
            prop(DataSeriesEntity::tenantId).isEqualTo(tenant.id)
            prop(DataSeriesEntity::creatorId).isEqualTo(creator.id)
            prop(DataSeriesEntity::displayName).isEqualTo("my other name")
            prop(DataSeriesEntity::dataType).isEqualTo(DataType.METERS)
            prop(DataSeriesEntity::filters).containsOnly(
                DataSeriesFilterEntity(
                    "campaign",
                    QueryClauseOperator.IS_NOT,
                    "AAA"
                )
            )
            prop(DataSeriesEntity::color).isEqualTo("#000000")
            prop(DataSeriesEntity::fieldName).isEqualTo("my-other-field")
            prop(DataSeriesEntity::aggregationOperation).isEqualTo(QueryAggregationOperator.MAX)
            prop(DataSeriesEntity::timeframeUnitMs).isEqualTo(240_000L)
            prop(DataSeriesEntity::displayFormat).isEqualTo("##0.0")
            prop(DataSeriesEntity::query).isEqualTo("This is the other query")
        }
    }

    @Test
    fun `should delete data series when the tenant is deleted`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        dataSeriesRepository.save(dataSeriesPrototype.copy(tenantId = tenant.id, creatorId = creator.id))
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(1)

        // when
        tenantRepository.deleteById(tenant.id)

        // then
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(0)
    }

    @Test
    fun `should throw an error when delete data series when the creator is deleted`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        dataSeriesRepository.save(dataSeriesPrototype.copy(tenantId = tenant.id, creatorId = creator.id))
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(1)

        // when
        assertThrows<DataAccessException> {
            userRepository.deleteById(creator.id)
        }

        // then
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(1)
    }
}