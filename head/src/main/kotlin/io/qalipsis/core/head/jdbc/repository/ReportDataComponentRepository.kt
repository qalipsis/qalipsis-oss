package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity

/**
 * Micronaut data repository to operate with [ReportDataComponentEntity].
 *
 * @author Joël Valère
 */

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface ReportDataComponentRepository : CoroutineCrudRepository<ReportDataComponentEntity, Long>{

    /**
     * Find a full data component by its internal ID.
     * The result contains the list of associated data series
     */
    @Join(value = "dataSeries", type = Join.Type.LEFT_FETCH)
    suspend fun findByIdInOrderById(ids: Collection<Long>) : Collection<ReportDataComponentEntity>

    /**
     * Delete a data component based on it associated report ID
     */
    suspend fun deleteByReportId(reportId: Long): Long
}