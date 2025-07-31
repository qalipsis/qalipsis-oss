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
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClause
import io.qalipsis.api.query.QueryClauseOperator.IS
import io.qalipsis.api.query.QueryDescription
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesCreationRequest
import io.qalipsis.core.head.model.DataSeriesFilter
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.security.TenantProvider
import io.qalipsis.core.head.security.UserProvider
import io.qalipsis.core.head.utils.SortingUtil
import io.qalipsis.core.head.utils.SqlFilterUtils.formatsFilters
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.launch
import io.qalipsis.api.query.Page as QalipsisPage

/**
 * Default implementation of [DataSeriesService] interface.
 *
 * @author Palina Bril
 */
@Singleton
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
class DataSeriesServiceImpl(
    private val dataSeriesRepository: DataSeriesRepository,
    private val tenantProvider: TenantProvider,
    private val userProvider: UserProvider,
    private val idGenerator: IdGenerator,
    private val dataProvider: DataProvider,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundScope: CoroutineScope,
) : DataSeriesService {

    override suspend fun get(tenant: String, username: String, reference: String): DataSeries {
        val dataSeriesEntity = dataSeriesRepository.findByTenantAndReference(tenant = tenant, reference = reference)
        val creatorName = userProvider.findUsernameById(dataSeriesEntity.creatorId) ?: ""
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
                tenantId = tenantProvider.findIdByReference(tenant),
                creatorId = requireNotNull(userProvider.findIdByUsername(creator)),
                displayName = dataSeries.displayName,
                sharingMode = dataSeries.sharingMode,
                dataType = dataSeries.dataType,
                valueName = dataSeries.valueName,
                color = dataSeries.color?.uppercase(),
                filters = dataSeries.filters.map { it.toEntity() },
                fieldName = dataSeries.fieldName,
                aggregationOperation = dataSeries.aggregationOperation ?: QueryAggregationOperator.COUNT,
                timeframeUnitMs = dataSeries.timeframeUnit?.toMillis(),
                displayFormat = dataSeries.displayFormat,
                query = dataProvider.createQuery(
                    tenant, dataSeries.dataType, QueryDescription(
                        filters = convertFilters(dataSeries.filters, dataSeries.valueName),
                        fieldName = dataSeries.fieldName,
                        aggregationOperation = dataSeries.aggregationOperation ?: QueryAggregationOperator.COUNT,
                        timeframeUnit = dataSeries.timeframeUnit
                    )
                ).takeUnless(String::isNullOrBlank),
                colorOpacity = dataSeries.colorOpacity
            )
        )
        return DataSeries(createdDataSeries, creator)
    }

    private fun convertFilters(filters: Collection<DataSeriesFilter>, valueName: String): Collection<QueryClause> {
        return filters.map { QueryClause("tag.${it.name}", it.operator, it.value) } + QueryClause("name", IS, valueName)
    }

    override suspend fun update(
        tenant: String,
        username: String,
        reference: String,
        patches: Collection<DataSeriesPatch>,
    ): DataSeries {
        val dataSeriesEntity = dataSeriesRepository.findByTenantAndReference(tenant, reference)
        val creatorName = userProvider.findUsernameById(dataSeriesEntity.creatorId) ?: ""
        if (username != creatorName && dataSeriesEntity.sharingMode != SharingMode.WRITE) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "You do not have the permission to update this data series")
        }
        val dataSeriesWasUpdated = patches.map { it.apply(dataSeriesEntity) }.any { it }
        val updatedDataSeries = if (dataSeriesWasUpdated) {
            dataSeriesEntity.query = dataProvider.createQuery(
                tenant, dataSeriesEntity.dataType, QueryDescription(
                    filters = convertFiltersEntity(dataSeriesEntity.filters, dataSeriesEntity.valueName),
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

    private fun convertFiltersEntity(
        filters: Collection<DataSeriesFilterEntity>,
        valueName: String,
    ): Collection<QueryClause> {
        return filters.map { QueryClause("tag.${it.name}", it.operator, it.value) } + QueryClause("name", IS, valueName)
    }

    override suspend fun delete(tenant: String, username: String, references: Set<String>) {
        val deletableDataSeriesReferences = dataSeriesRepository.findAllUpdatableReferencesByTenantAndReferences(tenant, references, username)
        if ((references - deletableDataSeriesReferences.toSet()).isNotEmpty()) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "You do not have the permission to delete this data series")
        }
        dataSeriesRepository.deleteAllByReference(references)
    }

    override suspend fun searchDataSeries(
        tenant: String,
        username: String,
        filters: Collection<String>,
        sort: String?,
        page: Int,
        size: Int,
    ): QalipsisPage<DataSeries> {
        val sorting = sort?.let { SortingUtil.sort(DataSeriesEntity::class, it) }
            ?: Sort.of(Sort.Order(DataSeriesEntity::displayName.name))
        val pageable = Pageable.from(page, size, sorting)

        val dataSeriesEntityPage = if (filters.isNotEmpty()) {
            dataSeriesRepository.searchDataSeries(
                tenant = tenant,
                username = username,
                filters = filters.formatsFilters(), pageable = pageable
            )
        } else {
            dataSeriesRepository.searchDataSeries(tenant = tenant, username = username, pageable = pageable)
        }
        val creatorsIds = dataSeriesEntityPage.content.map { it.creatorId }.toSet()
        val creatorsNamesByIds = if (creatorsIds.isNotEmpty()) {
            userProvider.findIdAndDisplayNameByIdIn(creatorsIds).associate { it.id to it.displayName }
        } else {
            emptyMap()
        }
        return QalipsisPage(
            page = dataSeriesEntityPage.pageNumber,
            totalPages = dataSeriesEntityPage.totalPages,
            totalElements = dataSeriesEntityPage.totalSize,
            elements = dataSeriesEntityPage.content.map { it.toModel(creatorsNamesByIds[it.creatorId] ?: "") }
        )
    }

    override suspend fun refresh() {
        backgroundScope.launch {
            var pageable = Pageable.from(0, SIZE)
            var pagedDataSeries: Page<DataSeriesEntity>
            do {
                pagedDataSeries = dataSeriesRepository.findAll(pageable)
                handleQueryUpdate(pagedDataSeries)
                pageable = pagedDataSeries.nextPageable()
            } while (pagedDataSeries.pageNumber < pagedDataSeries.totalPages - 1)
        }
    }

    /**
     * Handle the update of each query for a given batch of data series entity.
     */
    private suspend fun handleQueryUpdate(pagedDataSeries: Page<DataSeriesEntity>) {
        try {
            val tenantIdReference = mutableMapOf<Long, String>()
            val countOfUpdatedQueries = pagedDataSeries.numberOfElements
            logger.trace { "Updating the queries of ${pagedDataSeries.numberOfElements} retrieved data series" }
            val dataSeriesEntities = pagedDataSeries.content
            dataSeriesEntities.forEach { dataSeriesEntity ->
                val tenant = tenantIdReference.getOrPut(dataSeriesEntity.tenantId) {
                    tenantProvider.findReferenceById(dataSeriesEntity.tenantId)
                }
                dataSeriesEntity.query = dataProvider.createQuery(
                    tenant, dataSeriesEntity.dataType, QueryDescription(
                        filters = convertFiltersEntity(dataSeriesEntity.filters, dataSeriesEntity.valueName),
                        fieldName = dataSeriesEntity.fieldName,
                        aggregationOperation = dataSeriesEntity.aggregationOperation,
                        timeframeUnit = dataSeriesEntity.timeframeUnitAsDuration
                    )
                ).takeUnless(String::isNullOrBlank)
            }
            dataSeriesRepository.saveAll(dataSeriesEntities).count()
            logger.info { "Successfully updated $countOfUpdatedQueries queries of the data series" }
        } catch (ex: Exception) {
            logger.error(ex) { "Encountered an error while trying to update a data series: ${ex.message}" }
        }
    }

    companion object {
        val logger = logger()
        private const val SIZE = 10
    }
}