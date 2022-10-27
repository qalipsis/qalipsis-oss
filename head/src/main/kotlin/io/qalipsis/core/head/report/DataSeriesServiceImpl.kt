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

import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClause
import io.qalipsis.api.query.QueryDescription
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesCreationRequest
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.model.converter.DataSeriesConverter
import io.qalipsis.core.head.utils.SortingUtil
import jakarta.inject.Singleton
import io.qalipsis.api.query.Page as QalipsisPage

/**
 * Default implementation of [DataSeriesService] interface
 *
 * @author Palina Bril
 */
@Singleton
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal class DataSeriesServiceImpl(
    private val dataSeriesRepository: DataSeriesRepository,
    private val tenantRepository: TenantRepository,
    private val userRepository: UserRepository,
    private val idGenerator: IdGenerator,
    private val dataProvider: DataProvider,
    private val dataSeriesConverter: DataSeriesConverter
) : DataSeriesService {

    override suspend fun get(tenant: String, username: String, reference: String): DataSeries {
        val dataSeriesEntity = dataSeriesRepository.findByTenantAndReference(tenant = tenant, reference = reference)
        val creatorName = userRepository.findUsernameById(dataSeriesEntity.creatorId) ?: ""
        if (username != creatorName && dataSeriesEntity.sharingMode == SharingMode.NONE) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "You do not have the permission to use this data series")
        }
        return DataSeries(dataSeriesEntity, creatorName)
    }

    override suspend fun create(tenant: String, creator: String, dataSeries: DataSeriesCreationRequest): DataSeries {
        require(!dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(tenant, dataSeries.displayName)) {
            "A data series named ${dataSeries.displayName} already exists in your organization"
        }
        val aggregationOperation = dataSeries.aggregationOperation ?: QueryAggregationOperator.COUNT
        require(aggregationOperation == QueryAggregationOperator.COUNT || !dataSeries.fieldName.isNullOrBlank()) {
            "The field name should be set when the aggregation is not count"
        }
        val createdDataSeries = dataSeriesRepository.save(
            DataSeriesEntity(
                reference = idGenerator.short(),
                tenantId = tenantRepository.findIdByReference(tenant),
                creatorId = requireNotNull(userRepository.findIdByUsername(creator)),
                displayName = dataSeries.displayName,
                sharingMode = dataSeries.sharingMode,
                dataType = dataSeries.dataType,
                color = dataSeries.color?.uppercase(),
                filters = dataSeries.filters.map { it.toEntity() },
                fieldName = dataSeries.fieldName,
                aggregationOperation = dataSeries.aggregationOperation ?: QueryAggregationOperator.COUNT,
                timeframeUnitMs = dataSeries.timeframeUnit?.toMillis(),
                displayFormat = dataSeries.displayFormat,
                query = dataProvider.createQuery(
                    tenant, dataSeries.dataType, QueryDescription(
                        filters = dataSeries.filters.map { QueryClause(it.name, it.operator, it.value) },
                        fieldName = dataSeries.fieldName,
                        aggregationOperation = dataSeries.aggregationOperation ?: QueryAggregationOperator.COUNT,
                        timeframeUnit = dataSeries.timeframeUnit
                    )
                ).takeUnless(String::isNullOrBlank)
            )
        )
        return DataSeries(createdDataSeries, creator)
    }

    override suspend fun update(
        tenant: String,
        username: String,
        reference: String,
        patches: Collection<DataSeriesPatch>
    ): DataSeries {
        val dataSeriesEntity = dataSeriesRepository.findByTenantAndReference(tenant, reference)
        val creatorName = userRepository.findUsernameById(dataSeriesEntity.creatorId) ?: ""
        if (username != creatorName && dataSeriesEntity.sharingMode != SharingMode.WRITE) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "You do not have the permission to update this data series")
        }
        val dataSeriesWasUpdated = patches.map { it.apply(dataSeriesEntity) }.any { it }
        val updatedDataSeries = if (dataSeriesWasUpdated) {
            dataSeriesEntity.query = dataProvider.createQuery(
                tenant, dataSeriesEntity.dataType, QueryDescription(
                    filters = dataSeriesEntity.filters.map { QueryClause(it.name, it.operator, it.value) },
                    fieldName = dataSeriesEntity.fieldName,
                    aggregationOperation = dataSeriesEntity.aggregationOperation,
                    timeframeUnit = dataSeriesEntity.timeframeUnitAsDuration
                )
            ).takeUnless(String::isNullOrBlank)

            require(
                !dataSeriesRepository.existsByTenantReferenceAndDisplayNameAndIdNot(
                    tenant,
                    dataSeriesEntity.displayName,
                    dataSeriesEntity.id
                )
            ) {
                "A data series named ${dataSeriesEntity.displayName} already exists in your organization"
            }
            dataSeriesRepository.update(dataSeriesEntity)
        } else {
            dataSeriesEntity
        }
        return DataSeries(updatedDataSeries, creatorName)
    }

    override suspend fun delete(tenant: String, username: String, reference: String) {
        val dataSeriesEntity = dataSeriesRepository.findByTenantAndReference(tenant, reference)
        if (dataSeriesEntity.sharingMode != SharingMode.WRITE && username != userRepository.findUsernameById(
                dataSeriesEntity.creatorId
            )
        ) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "You do not have the permission to delete this data series")
        }
        dataSeriesRepository.delete(dataSeriesEntity)
    }

    override suspend fun searchDataSeries(
        tenant: String,
        username: String,
        filters: Collection<String>,
        sort: String?,
        page: Int,
        size: Int
    ): QalipsisPage<DataSeries> {
        val sorting = sort?.let { SortingUtil.sort(DataSeriesEntity::class, it) }
            ?: Sort.of(Sort.Order(DataSeriesEntity::displayName.name))
        val pageable = Pageable.from(page, size, sorting)

        val dataSeriesEntityPage = if (filters.isNotEmpty()) {
            val sanitizedFilters = filters.map { it.replace('*', '%').replace('?', '_') }.map { "%${it.trim()}%" }
            dataSeriesRepository.searchDataSeries(tenant, username, sanitizedFilters, pageable)
        } else {
            dataSeriesRepository.searchDataSeries(tenant, username, pageable)
        }
        return QalipsisPage(
            page = dataSeriesEntityPage.pageNumber,
            totalPages = dataSeriesEntityPage.totalPages,
            totalElements = dataSeriesEntityPage.totalSize,
            elements = dataSeriesEntityPage.content.map { dataSeriesConverter.convertToModel(it) }
        )
    }

}