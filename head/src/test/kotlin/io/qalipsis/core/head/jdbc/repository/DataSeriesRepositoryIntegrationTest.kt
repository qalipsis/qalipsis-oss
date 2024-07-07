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
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.report.SharingMode
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class DataSeriesRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var dataSeriesRepository: DataSeriesRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var userRepository: UserRepository

    private lateinit var defaultTenant: TenantEntity

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
            valueName = "my-value",
            color = "#FFFFFF",
            filters = setOf(DataSeriesFilterEntity("minionsCount", QueryClauseOperator.IS, "1000")),
            timeframeUnitMs = 10_000L,
            fieldName = "my-field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            displayFormat = "#000.000",
            query = "This is the query",
            colorOpacity = 50
        )

    @BeforeEach
    fun setup() = testDispatcherProvider.run {
        tenantRepository.deleteAll()
        defaultTenant = tenantRepository.save(
            tenantPrototype.copy(
                reference = Defaults.TENANT,
                displayName = Defaults.TENANT
            )
        )
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        dataSeriesRepository.deleteAll()
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
                dataType = DataType.METERS,
                valueName = "my-value",
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
            prop(DataSeriesEntity::colorOpacity).isNull()
        }

        // when searching a data series with same name but other ID
        var existsWithSameNameAndOtherId =
            dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(tenant.reference, "my-name", saved.id)

        // then
        assertThat(existsWithSameNameAndOtherId).isFalse()

        // when searching a data series with other name and other ID
        existsWithSameNameAndOtherId = dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
            tenant.reference,
            "my-other-name",
            saved.id
        )

        // then
        assertThat(existsWithSameNameAndOtherId).isFalse()

        // when searching a data series with same name and any ID
        existsWithSameNameAndOtherId =
            dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(tenant.reference, "my-name")

        // then
        assertThat(existsWithSameNameAndOtherId).isTrue()
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
                dataType = DataType.METERS,
                valueName = "my-value",
            )
        )

        // when
        assertThrows<R2dbcDataIntegrityViolationException> {
            dataSeriesRepository.save(
                DataSeriesEntity(
                    reference = "other-series",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "my-name",
                    dataType = DataType.METERS,
                    valueName = "my-value",
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
                dataType = DataType.METERS,
                valueName = "my-value",
            )
        )

        // when
        assertThrows<R2dbcDataIntegrityViolationException> {
            dataSeriesRepository.save(
                DataSeriesEntity(
                    reference = "my-series",
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "other-name",
                    dataType = DataType.METERS,
                    valueName = "my-value",
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
                dataType = DataType.METERS,
                valueName = "my-value",
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
                    dataType = DataType.METERS,
                    valueName = "my-value",
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
            prop(DataSeriesEntity::colorOpacity).isEqualTo(50)
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
            query = "This is the other query",
            colorOpacity = 75
        )
        val beforeUpdate = Instant.now()
        dataSeriesRepository.update(updated)
        fetched = dataSeriesRepository.findByTenantAndReference(tenant = "my-tenant", reference = "my-series")

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
            prop(DataSeriesEntity::colorOpacity).isEqualTo(75)
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
        assertThrows<R2dbcDataIntegrityViolationException> {
            userRepository.deleteById(creator.id)
        }

        // then
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(1)
    }

    @Test
    fun `should fetch all data series with the default search params`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val dataSeries =
            dataSeriesRepository.save(dataSeriesPrototype.copy(tenantId = tenant.id, creatorId = creator.id))
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(1)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            pageable = Pageable.from(0, 1, Sort.of(Sort.Order("displayName")))
        ).content

        //then
        assertThat(dataSeriesEntities).all {
            hasSize(1)
            index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries.id)
        }
    }

    @Test
    fun `should fetch all data series and sort them by the specified sort param`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                color = "red"
            )
        )
        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                color = "green",
                displayName = "my-display-name",
                reference = "my-series-2"
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            pageable = Pageable.from(0, 2, Sort.of(Sort.Order("color")))
        )

        //then
        assertThat(dataSeriesEntities.content).all {
            hasSize(2)
            index(0).prop(DataSeriesEntity::color).isEqualTo("green")
            index(1).prop(DataSeriesEntity::color).isEqualTo("red")
        }
    }

    @Test
    fun `should return data series that belong to user alone or sharing mode is not none`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))
            val dataSeries1 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                )
            )
            val dataSeriesWithNoSharingMode = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-2",
                    sharingMode = SharingMode.NONE,
                    displayName = "my-name-2",
                )
            )
            val dataSeriesWithWriteSharingMode = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-3",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-name-3"
                )
            )
            val dataSeriesWithReadSharingMode = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-4",
                    sharingMode = SharingMode.READONLY,
                    displayName = "my-name-4"
                )
            )
            assertThat(dataSeriesRepository.findAll().count()).isEqualTo(4)

            //when
            val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
                tenant = tenant.reference,
                username = creator.username,
                pageable = Pageable.from(0, 3, Sort.of(Sort.Order("displayName")))
            ).content

            //then
            assertThat(dataSeriesEntities).all {
                hasSize(3)
                index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries1.id)
                index(1).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithWriteSharingMode.id)
                index(2).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithReadSharingMode.id)
            }
            assertThat(dataSeriesEntities).doesNotContain(dataSeriesWithNoSharingMode)
        }

    @Test
    fun `should fetch only data series belonging to only the specified tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val otherTenant = tenantRepository.save(tenantPrototype.copy(reference = "other-tenant"))
        val creator = userRepository.save(userPrototype.copy())
        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
            )
        )
        val dataSeriesWithDefaultTenant = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = otherTenant.id,
                creatorId = creator.id,
                displayName = "my-display-name",
                reference = "my-series-2"
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = otherTenant.reference,
            username = creator.username,
            pageable = Pageable.from(0, 1, Sort.of(Sort.Order("displayName")))
        ).content

        //then
        assertThat(dataSeriesEntities).all {
            hasSize(1)
            index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithDefaultTenant.id)
        }
    }

    @Test
    fun `should find all the data series with filter on fieldName`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))

        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                sharingMode = SharingMode.NONE
            )
        )
        val dataSeriesWithFieldName = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-display-name",
                reference = "my-series-2",
                fieldName = "field-1",
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            filters = listOf("%I_l%1%"),
            pageable = Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
        ).content

        //then
        assertThat(dataSeriesEntities).all {
            hasSize(1)
            index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithFieldName.id)
            index(0).prop(DataSeriesEntity::fieldName).isEqualTo(dataSeriesWithFieldName.fieldName)
        }
    }

    @Test
    fun `should find all the data series with filter on data type`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))

        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                sharingMode = SharingMode.NONE
            )
        )
        val dataSeriesWithFieldName = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-display-name",
                reference = "my-series-2",
                dataType = DataType.EVENTS,
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            filters = listOf("%even%"),
            pageable = Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
        ).content

        //then
        assertThat(dataSeriesEntities).all {
            hasSize(1)
            index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithFieldName.id)
            index(0).prop(DataSeriesEntity::dataType).isEqualTo(dataSeriesWithFieldName.dataType)
        }
    }

    @Test
    fun `should find all the data series with filter on display name`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))

        val dataSeries = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                sharingMode = SharingMode.NONE
            )
        )
        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-display-name",
                reference = "my-series-2",
                dataType = DataType.EVENTS,
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            filters = listOf("%my-naME%"),
            pageable = Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
        ).content

        //then
        assertThat(dataSeriesEntities).all {
            hasSize(1)
            index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries.id)
            index(0).prop(DataSeriesEntity::displayName).isEqualTo(dataSeries.displayName)
        }
    }

    @Test
    fun `should find all the data series with filter on user name`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))

        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                sharingMode = SharingMode.NONE
            )
        )
        val dataSeries = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-display-name",
                reference = "my-series-2",
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            filters = listOf("%another%"),
            pageable = Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
        ).content

        //then
        assertThat(dataSeriesEntities).all {
            hasSize(1)
            index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries.id)
        }
    }

    @Test
    fun `should find all the data series with filter on creator display name`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator =
            userRepository.save(userPrototype.copy(username = "another-user", displayName = "unique-user"))

        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
            )
        )
        val dataSeries = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-display-name",
                reference = "my-series-2",
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            filters = listOf("%unique%"),
            pageable = Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
        ).content

        //then
        assertThat(dataSeriesEntities).all {
            hasSize(1)
            index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries.id)
        }
    }

    @Test
    fun `should fetch nothing if filters don't match`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))

        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                sharingMode = SharingMode.READONLY
            )
        )
        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                displayName = "my-display-name",
                reference = "my-series-2",
                sharingMode = SharingMode.NONE
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(2)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            filters = listOf("%minionsCount%"),
            pageable = Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
        ).content

        //then
        assertThat(dataSeriesEntities).isEmpty()
    }

    @Test
    fun `should retrieve only the data series that match the given search criteria`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype.copy())
        val creator = userRepository.save(userPrototype.copy())
        val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user", displayName = "unique"))

        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
            )
        )

        val dataSeriesWithIdealUser = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = anotherCreator.id,
                reference = "my-series-2",
                fieldName = "field-7",
                sharingMode = SharingMode.WRITE,
                displayName = "my-display-name"
            )
        )
        val dataSeriesWithIdealField = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                displayName = "ds-with-user",
                reference = "my-series-3",
                fieldName = "foo"
            )
        )
        assertThat(dataSeriesRepository.findAll().count()).isEqualTo(3)

        //when
        val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
            tenant = tenant.reference,
            username = creator.username,
            filters = listOf("%foo%", "%uNIQ%"),
            pageable = Pageable.from(0, 2, Sort.of(Sort.Order("displayName")))
        ).content
        assertThat(dataSeriesEntities.size).isEqualTo(2)

        //then
        assertThat(dataSeriesEntities).all {
            hasSize(2)
            index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithIdealField.id)
            index(0).prop(DataSeriesEntity::reference).isEqualTo(dataSeriesWithIdealField.reference)
            index(0).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeriesWithIdealField.creatorId)
            index(0).prop(DataSeriesEntity::displayName).isEqualTo(dataSeriesWithIdealField.displayName)
            index(0).prop(DataSeriesEntity::tenantId).isEqualTo(dataSeriesWithIdealField.tenantId)
            index(1).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithIdealUser.id)
            index(1).prop(DataSeriesEntity::reference).isEqualTo(dataSeriesWithIdealUser.reference)
            index(1).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeriesWithIdealUser.creatorId)
            index(1).prop(DataSeriesEntity::displayName).isEqualTo(dataSeriesWithIdealUser.displayName)
            index(1).prop(DataSeriesEntity::tenantId).isEqualTo(dataSeriesWithIdealUser.tenantId)
        }

        assertThat(dataSeriesEntities[1]).isNotNull().all {
            prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithIdealUser.id)
            prop(DataSeriesEntity::reference).isEqualTo(dataSeriesWithIdealUser.reference)
            prop(DataSeriesEntity::creatorId).isEqualTo(dataSeriesWithIdealUser.creatorId)
            prop(DataSeriesEntity::displayName).isEqualTo(dataSeriesWithIdealUser.displayName)
            prop(DataSeriesEntity::tenantId).isEqualTo(dataSeriesWithIdealUser.tenantId)
        }
    }

    @Test
    fun `should save then successfully check existence based on reference and tenant`() = testDispatcherProvider.run {
        // given
        val tenant = tenantRepository.save(tenantPrototype)
        val creator = userRepository.save(userPrototype)
        dataSeriesRepository.save(dataSeriesPrototype.copy(tenantId = tenant.id, creatorId = creator.id))

        //when + then
        assertThat(
            dataSeriesRepository.checkExistenceByTenantAndReference(
                tenant = "my-tenant",
                reference = "my-series-1"
            )
        ).isFalse()
        assertThat(
            dataSeriesRepository.checkExistenceByTenantAndReference(
                tenant = "my-tenant",
                reference = "my-series"
            )
        ).isTrue()
        assertThat(
            dataSeriesRepository.checkExistenceByTenantAndReference(
                tenant = "my-tenant-1",
                reference = "my-series"
            )
        ).isFalse()
    }

    @Test
    fun `should fetch the requested dataSeries including the default`() = testDispatcherProvider.run {
        //given
        val tenant = tenantRepository.save(tenantPrototype)
        val creator = userRepository.save(userPrototype)
        val defaultCreator = userRepository.save(
            userPrototype.copy(
                username = "default-user",
                displayName = "Default test user for test"
            )
        )
        val randomCreator = userRepository.save(
            userPrototype.copy(
                username = "random-user",
                displayName = "random test user for test"
            )
        )
        val randomTenant =
            tenantRepository.save(tenantPrototype.copy(reference = "random_tenant", displayName = "random_tenant"))
        val redDataSeries = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                color = "red",
                sharingMode = SharingMode.WRITE
            )
        )
        val defaultDataSeries = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = defaultTenant.id,
                creatorId = defaultCreator.id,
                color = "purple",
                displayName = "my-default-name",
                reference = "my-series-2",
                sharingMode = SharingMode.WRITE
            )
        )
        val series3 = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                color = "green",
                displayName = "my-green-name",
                reference = "my-series-3",
                sharingMode = SharingMode.NONE
            )
        )
        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = randomTenant.id,
                creatorId = randomCreator.id,
                color = "blue",
                displayName = "my-blue-name",
                reference = "my-series-4",
                sharingMode = SharingMode.READONLY
            )
        )
        dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                color = "brown",
                displayName = "my-brown-name",
                reference = "my-series-5",
                sharingMode = SharingMode.NONE
            )
        )
        val series6 = dataSeriesRepository.save(
            dataSeriesPrototype.copy(
                tenantId = tenant.id,
                creatorId = creator.id,
                color = "yellow",
                displayName = "my-yellow-name",
                reference = "my-series-6",
                sharingMode = SharingMode.WRITE
            )
        )

        //when
        val found = dataSeriesRepository.findAllByTenantAndReferences(
            tenant.reference,
            listOf("my-series", "my-series-2", "my-series-3", "my-series-4", "my-series-6")
        ).sortedBy { it.id }

        //then
        assertThat(found).all {
            hasSize(4)
            index(0).prop(DataSeriesEntity::id).isEqualTo(redDataSeries.id)
            index(0).prop(DataSeriesEntity::creatorId).isEqualTo(redDataSeries.creatorId)
            index(0).prop(DataSeriesEntity::sharingMode).isEqualTo(redDataSeries.sharingMode)
            index(1).prop(DataSeriesEntity::id).isEqualTo(defaultDataSeries.id)
            index(1).prop(DataSeriesEntity::reference).isEqualTo(defaultDataSeries.reference)
            index(1).prop(DataSeriesEntity::creatorId).isEqualTo(-1)
            index(1).prop(DataSeriesEntity::tenantId).isEqualTo(defaultTenant.id)
            index(1).prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.READONLY)
            index(2).prop(DataSeriesEntity::id).isEqualTo(series3.id)
            index(2).prop(DataSeriesEntity::creatorId).isEqualTo(series3.creatorId)
            index(2).prop(DataSeriesEntity::sharingMode).isEqualTo(series3.sharingMode)
            index(3).prop(DataSeriesEntity::id).isEqualTo(series6.id)
            index(3).prop(DataSeriesEntity::creatorId).isEqualTo(series6.creatorId)
            index(3).prop(DataSeriesEntity::sharingMode).isEqualTo(series6.sharingMode)
        }
    }

    @Test
    fun `should retrieve all the data series including the default ones if it matches the given search criteria`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val anotherCreator =
                userRepository.save(userPrototype.copy(username = "another-user", displayName = "unique"))
            val defaultCreator = userRepository.save(
                userPrototype.copy(
                    username = "default-user",
                    displayName = "Default test user for test"
                )
            )
            dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                )
            )
            val dataSeriesWithIdealUser = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-2",
                    fieldName = "field-7",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-display-name"
                )
            )
            val dataSeriesWithIdealField = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "ds-with-user",
                    reference = "my-series-3",
                    fieldName = "foo"
                )
            )
            val defaultDataSeries = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = defaultTenant.id,
                    creatorId = defaultCreator.id,
                    color = "purple",
                    displayName = "default-purple",
                    reference = "my-series-5",
                    sharingMode = SharingMode.WRITE
                )
            )
            assertThat(dataSeriesRepository.findAll().count()).isEqualTo(4)

            //when
            val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
                tenant = tenant.reference,
                username = creator.username,
                filters = listOf("%foo%", "%uNIQ%", "%DEfAu%"),
                pageable = Pageable.from(0, 3, Sort.of(Sort.Order("displayName")))
            ).content
            assertThat(dataSeriesEntities.size).isEqualTo(3)
            assertThat(dataSeriesEntities.contains(defaultDataSeries))
            //then
            assertThat(dataSeriesEntities).all {
                hasSize(3)
                index(0).prop(DataSeriesEntity::id).isEqualTo(defaultDataSeries.id)
                index(0).prop(DataSeriesEntity::tenantId).isEqualTo(defaultTenant.id)
                index(0).prop(DataSeriesEntity::creatorId).isEqualTo(-1)
                index(0).prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.READONLY)
                index(1).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithIdealField.id)
                index(1).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeriesWithIdealField.creatorId)
                index(1).prop(DataSeriesEntity::sharingMode).isEqualTo(dataSeriesWithIdealField.sharingMode)
                index(2).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithIdealUser.id)
                index(2).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeriesWithIdealUser.creatorId)
                index(2).prop(DataSeriesEntity::sharingMode).isEqualTo(dataSeriesWithIdealUser.sharingMode)
            }
        }

    @Test
    fun `should retrieve all the data series including the default one when no search filter is passed in`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val anotherCreator =
                userRepository.save(userPrototype.copy(username = "another-user", displayName = "unique"))
            val defaultCreator = userRepository.save(
                userPrototype.copy(
                    username = "default-user",
                    displayName = "Default test user for test"
                )
            )
            dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                )
            )
            val dataSeriesWithIdealUser = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-2",
                    fieldName = "field-7",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-display-name"
                )
            )
            val dataSeriesWithIdealField = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "ds-with-user",
                    reference = "my-series-3",
                    fieldName = "foo"
                )
            )
            val defaultDataSeries = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = defaultTenant.id,
                    creatorId = defaultCreator.id,
                    color = "purple",
                    displayName = "default-purple",
                    reference = "my-series-5",
                    sharingMode = SharingMode.WRITE
                )
            )
            assertThat(dataSeriesRepository.findAll().count()).isEqualTo(4)

            //when
            val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
                tenant = tenant.reference,
                username = creator.username,
                pageable = Pageable.from(0, 3, Sort.of(Sort.Order("displayName")))
            ).content
            assertThat(dataSeriesEntities.size).isEqualTo(3)
            assertThat(dataSeriesEntities.contains(defaultDataSeries))

            //then
            assertThat(dataSeriesEntities).all {
                hasSize(3)
                index(0).prop(DataSeriesEntity::id).isEqualTo(defaultDataSeries.id)
                index(0).prop(DataSeriesEntity::tenantId).isEqualTo(defaultTenant.id)
                index(0).prop(DataSeriesEntity::creatorId).isEqualTo(-1)
                index(0).prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.READONLY)
                index(1).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithIdealField.id)
                index(1).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeriesWithIdealField.creatorId)
                index(1).prop(DataSeriesEntity::sharingMode).isEqualTo(dataSeriesWithIdealField.sharingMode)
                index(2).prop(DataSeriesEntity::id).isEqualTo(dataSeriesWithIdealUser.id)
                index(2).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeriesWithIdealUser.creatorId)
                index(2).prop(DataSeriesEntity::sharingMode).isEqualTo(dataSeriesWithIdealUser.sharingMode)
            }
        }

    @Test
    fun `should not retrieve the default data series if it doesn't match the given search criteria`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val anotherCreator =
                userRepository.save(userPrototype.copy(username = "another-user", displayName = "unique"))
            val defaultCreator = userRepository.save(
                userPrototype.copy(
                    username = "series-2-user",
                    displayName = "Test for user"
                )
            )
            dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                )
            )
            dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-2",
                    fieldName = "field-7",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-display-name"
                )
            )
            dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "ds-with-user",
                    reference = "my-series-3",
                    fieldName = "foo"
                )
            )
            val defaultDataSeries = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = defaultTenant.id,
                    creatorId = defaultCreator.id,
                    color = "purple",
                    displayName = "purple-series",
                    reference = "my-series-5",
                    sharingMode = SharingMode.WRITE
                )
            )
            assertThat(dataSeriesRepository.findAll().count()).isEqualTo(4)

            //when
            val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
                tenant = tenant.reference,
                username = creator.username,
                filters = listOf("%foo%", "%uNIQ%", "%DEfAu%"),
                pageable = Pageable.from(0, 3, Sort.of(Sort.Order("displayName")))
            ).content
            assertThat(dataSeriesEntities.size).isEqualTo(2)
            assertThat(dataSeriesEntities).doesNotContain(defaultDataSeries)
        }

    @Test
    fun `should return a paginated list of all the data series`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val creator = userRepository.save(userPrototype.copy())
            val dataSeries1 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                )
            )
            val dataSeries2 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    reference = "my-series-2",
                    fieldName = "field-7",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-display-name"
                )
            )
            val dataSeries3 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "ds-with-user",
                    reference = "my-series-3",
                    fieldName = "foo"
                )
            )
            assertThat(dataSeriesRepository.findAll().count()).isEqualTo(3)
            val pageable = Pageable.from(0, 2)

            //when
            val dataSeriesEntities = dataSeriesRepository.findAll(pageable)

            //then
            assertThat(dataSeriesEntities.size).isEqualTo(2)
            assertThat(dataSeriesEntities.totalPages).isEqualTo(2)
            assertThat(dataSeriesEntities.pageNumber).isEqualTo(0)
            assertThat(dataSeriesEntities.totalSize).isEqualTo(3)
            assertThat(dataSeriesEntities.content).all {
                hasSize(2)
                index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries1.id)
                index(0).prop(DataSeriesEntity::tenantId).isEqualTo(dataSeries1.tenantId)
                index(0).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeries1.creatorId)
                index(0).prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.READONLY)
                index(1).prop(DataSeriesEntity::id).isEqualTo(dataSeries2.id)
                index(1).prop(DataSeriesEntity::tenantId).isEqualTo(dataSeries2.tenantId)
                index(1).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeries2.creatorId)
                index(1).prop(DataSeriesEntity::sharingMode).isEqualTo(dataSeries2.sharingMode)
            }
            val nextPageable = dataSeriesEntities.nextPageable()
            val nextPageDataSeries = dataSeriesRepository.findAll(nextPageable)
            assertThat(nextPageDataSeries.size).isEqualTo(2)
            assertThat(nextPageDataSeries.pageNumber).isEqualTo(1)
            assertThat(nextPageDataSeries.content).all {
                hasSize(1)
                index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries3.id)
                index(0).prop(DataSeriesEntity::tenantId).isEqualTo(dataSeries3.tenantId)
                index(0).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeries3.creatorId)
                index(0).prop(DataSeriesEntity::sharingMode).isEqualTo(dataSeries3.sharingMode)
            }
        }

    @Test
    fun `should return a paginated list of all the data series grouped by their tenant ids`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val tenant2 = tenantRepository.save(tenantPrototype.copy(reference = "ten-2"))
            val creator = userRepository.save(userPrototype.copy())
            val dataSeries1 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                )
            )
            val dataSeries2 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant2.id,
                    creatorId = creator.id,
                    reference = "my-series-2",
                    fieldName = "field-7",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-display-name"
                )
            )
            val dataSeries3 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    displayName = "ds-with-user",
                    reference = "my-series-3",
                    fieldName = "foo"
                )
            )
            assertThat(dataSeriesRepository.findAll().count()).isEqualTo(3)
            val pageable = Pageable.from(0, 2)

            //when
            val dataSeriesEntities = dataSeriesRepository.findAll(pageable)

            //then
            assertThat(dataSeriesEntities.size).isEqualTo(2)
            assertThat(dataSeriesEntities.totalPages).isEqualTo(2)
            assertThat(dataSeriesEntities.pageNumber).isEqualTo(0)
            assertThat(dataSeriesEntities.totalSize).isEqualTo(3)
            assertThat(dataSeriesEntities.content).all {
                hasSize(2)
                index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries1.id)
                index(0).prop(DataSeriesEntity::tenantId).isEqualTo(dataSeries1.tenantId)
                index(0).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeries1.creatorId)
                index(0).prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.READONLY)
                index(1).prop(DataSeriesEntity::id).isEqualTo(dataSeries3.id)
                index(1).prop(DataSeriesEntity::tenantId).isEqualTo(dataSeries3.tenantId)
                index(1).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeries3.creatorId)
                index(1).prop(DataSeriesEntity::sharingMode).isEqualTo(dataSeries3.sharingMode)
            }
            val nextPageable = dataSeriesEntities.nextPageable()
            val nextPageDataSeries = dataSeriesRepository.findAll(nextPageable)
            assertThat(nextPageDataSeries.size).isEqualTo(2)
            assertThat(nextPageDataSeries.pageNumber).isEqualTo(1)
            assertThat(nextPageDataSeries.content).all {
                hasSize(1)
                index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries2.id)
                index(0).prop(DataSeriesEntity::tenantId).isEqualTo(dataSeries2.tenantId)
                index(0).prop(DataSeriesEntity::creatorId).isEqualTo(dataSeries2.creatorId)
                index(0).prop(DataSeriesEntity::sharingMode).isEqualTo(dataSeries2.sharingMode)
            }
        }

    @Test
    fun `should return only the data series for tenant with a not-none sharing mode`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val tenant2 = tenantRepository.save(tenantPrototype.copy(reference = "second-tenant"))
            val creator = userRepository.save(userPrototype.copy())
            val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))
            val dataSeries1 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = creator.id,
                    sharingMode = SharingMode.WRITE
                )
            )
            val dataSeries2 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-2",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-name-2",
                )
            )
            val dataSeries3 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant2.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-3",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-name-3"
                )
            )
            val dataSeries4 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant2.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-4",
                    sharingMode = SharingMode.READONLY,
                    displayName = "my-name-4"
                )
            )
            val dataSeries5 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant2.id,
                    creatorId = creator.id,
                    reference = "my-series-5",
                    sharingMode = SharingMode.NONE,
                    displayName = "my-name-5"
                )
            )
            val dataSeries6 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant2.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-6",
                    sharingMode = SharingMode.NONE,
                    displayName = "my-name-6"
                )
            )
            assertThat(dataSeriesRepository.findAll().count()).isEqualTo(6)

            //when
            val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
                tenant = tenant2.reference,
                username = creator.username,
                pageable = Pageable.from(0, 3, Sort.of(Sort.Order("displayName")))
            ).content

            //then
            assertThat(dataSeriesEntities).all {
                hasSize(3)
                index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries3.id)
                index(1).prop(DataSeriesEntity::id).isEqualTo(dataSeries4.id)
                index(2).prop(DataSeriesEntity::id).isEqualTo(dataSeries5.id)
            }
            assertThat(dataSeriesEntities).doesNotContain(dataSeries1)
            assertThat(dataSeriesEntities).doesNotContain(dataSeries2)
            assertThat(dataSeriesEntities).doesNotContain(dataSeries6)
        }

    @Test
    fun `should fetch the data series belonging to the default tenant`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(tenantPrototype.copy())
            val tenant2 = tenantRepository.save(tenantPrototype.copy(reference = "second-tenant"))
            val creator = userRepository.save(userPrototype.copy())
            val anotherCreator = userRepository.save(userPrototype.copy(username = "another-user"))
            val dataSeries1 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = defaultTenant.id,
                    creatorId = anotherCreator.id,
                    sharingMode = SharingMode.WRITE
                )
            )
            val dataSeries2 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-2",
                    sharingMode = SharingMode.WRITE,
                    displayName = "my-name-2",
                )
            )
            val dataSeries3 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant2.id,
                    creatorId = anotherCreator.id,
                    reference = "my-series-3",
                    sharingMode = SharingMode.NONE,
                    displayName = "my-name-3"
                )
            )
            val dataSeries4 = dataSeriesRepository.save(
                dataSeriesPrototype.copy(
                    tenantId = tenant2.id,
                    creatorId = creator.id,
                    reference = "my-series-4",
                    sharingMode = SharingMode.NONE,
                    displayName = "my-name-4"
                )
            )
            assertThat(dataSeriesRepository.findAll().count()).isEqualTo(4)

            //when
            val dataSeriesEntities = dataSeriesRepository.searchDataSeries(
                tenant = tenant2.reference,
                username = creator.username,
                pageable = Pageable.from(0, 3, Sort.of(Sort.Order("displayName")))
            ).content

            //then
            assertThat(dataSeriesEntities).all {
                hasSize(2)
                index(0).prop(DataSeriesEntity::id).isEqualTo(dataSeries1.id)
                index(1).prop(DataSeriesEntity::id).isEqualTo(dataSeries4.id)
            }
            assertThat(dataSeriesEntities).doesNotContain(dataSeries2)
            assertThat(dataSeriesEntities).doesNotContain(dataSeries3)
        }

}