package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.AggregationOperation
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.SharingMode
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
@Requires(notEnv = [ExecutionEnvironments.VOLATILE])
internal class DataSeriesServiceImpl(
    private val dataSeriesRepository: DataSeriesRepository,
    private val tenantRepository: TenantRepository,
    private val userRepository: UserRepository,
    private val idGenerator: IdGenerator
) : DataSeriesService {

    override suspend fun get(username: String, tenant: String, reference: String): DataSeries {
        val dataSeriesEntity = dataSeriesRepository.findByReferenceAndTenant(reference = reference, tenant = tenant)
        val creatorName = userRepository.findUsernameById(dataSeriesEntity.creatorId)
        if (username != creatorName && dataSeriesEntity.sharingMode == SharingMode.NONE) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "You do not have the permission to use this data series")
        }
        require(username == creatorName || dataSeriesEntity.sharingMode != SharingMode.NONE) { "You do not have the permission to use this data series" }
        return DataSeries(dataSeriesEntity, creatorName)
    }

    override suspend fun create(creator: String, tenant: String, dataSeries: DataSeries): DataSeries {
        val aggregationOperation = dataSeries.aggregationOperation ?: AggregationOperation.COUNT
        require(aggregationOperation == AggregationOperation.COUNT || !dataSeries.fieldName.isNullOrBlank()) {
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
                aggregationOperation = dataSeries.aggregationOperation ?: AggregationOperation.COUNT,
                timeframeUnitMs = dataSeries.timeframeUnit?.toMillis(),
                displayFormat = dataSeries.displayFormat,
                // TODO Add the generation of the query.
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
            dataSeriesRepository.update(dataSeriesEntity)
        } else {
            dataSeriesEntity
        }
        // TODO Add the generation of the query.
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