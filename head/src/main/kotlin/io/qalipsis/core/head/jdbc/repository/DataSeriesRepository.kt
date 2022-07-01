package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity

/**
 * Micronaut data repository to operate with [DataSeriesEntity].
 *
 * @author Palina Bril
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.VOLATILE])
internal interface DataSeriesRepository : CoroutineCrudRepository<DataSeriesEntity, Long> {

    @Query(
        """SELECT *
            FROM data_series
            WHERE reference = :reference AND EXISTS (SELECT 1 FROM tenant WHERE data_series.tenant_id = tenant.id AND tenant.reference = :tenant)"""
    )
    suspend fun findByReferenceAndTenant(reference: String, tenant: String): DataSeriesEntity

    @Query(
        """SELECT query
            FROM data_series
            WHERE reference = :reference AND EXISTS (SELECT 1 FROM tenant WHERE data_series.tenant_id = tenant.id AND tenant.reference = :tenant)"""
    )
    suspend fun findQueryByReferenceAndTenant(reference: String, tenant: String): String

}