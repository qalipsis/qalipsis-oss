package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.report.query.QueryAggregationOperator
import io.qalipsis.api.report.query.QueryClause
import io.qalipsis.api.report.query.QueryDescription
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesPatch
import jakarta.inject.Singleton

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
    private val dataProvider: DataProvider
) : DataSeriesService {

    override suspend fun get(username: String, tenant: String, reference: String): DataSeries {
        val dataSeriesEntity = dataSeriesRepository.findByReferenceAndTenant(reference = reference, tenant = tenant)
        val creatorName = userRepository.findUsernameById(dataSeriesEntity.creatorId)
        if (username != creatorName && dataSeriesEntity.sharingMode == SharingMode.NONE) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "You do not have the permission to use this data series")
        }
        return DataSeries(dataSeriesEntity, creatorName)
    }

    override suspend fun create(creator: String, tenant: String, dataSeries: DataSeries): DataSeries {
        val aggregationOperation = dataSeries.aggregationOperation ?: QueryAggregationOperator.COUNT
        require(aggregationOperation == QueryAggregationOperator.COUNT || !dataSeries.fieldName.isNullOrBlank()) {
            "The field name should be set when the aggregation is not count"
        }
        val createdDataSeries = dataSeriesRepository.save(
            DataSeriesEntity(
                reference = idGenerator.short(),
                tenantId = tenantRepository.findIdByReference(tenant),
                creatorId = userRepository.findIdByUsername(creator),
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
        username: String,
        tenant: String,
        reference: String,
        patches: Collection<DataSeriesPatch>
    ): DataSeries {
        val dataSeriesEntity = dataSeriesRepository.findByReferenceAndTenant(reference, tenant)
        val creatorName = userRepository.findUsernameById(dataSeriesEntity.creatorId)
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
            dataSeriesRepository.update(dataSeriesEntity)
        } else {
            dataSeriesEntity
        }
        return DataSeries(updatedDataSeries, creatorName)
    }

    override suspend fun delete(username: String, tenant: String, reference: String) {
        val dataSeriesEntity = dataSeriesRepository.findByReferenceAndTenant(reference, tenant)
        if (dataSeriesEntity.sharingMode != SharingMode.WRITE && username != userRepository.findUsernameById(
                dataSeriesEntity.creatorId
            )
        ) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "You do not have the permission to delete this data series")
        }
        dataSeriesRepository.delete(dataSeriesEntity)
    }
}