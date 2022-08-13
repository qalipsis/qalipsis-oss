package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
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
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
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

    @Query(
        value = """SELECT * 
            FROM data_series AS data_series_entity_
            LEFT JOIN "user" u ON data_series_entity_.creator_id = u.id
            WHERE 
            (u.username = :username OR data_series_entity_.sharing_mode <> 'NONE')
            AND (
             data_series_entity_.field_name ILIKE any (array[:filters]) 
             OR data_series_entity_.data_type ILIKE any (array[:filters])
             OR data_series_entity_.display_name ILIKE any (array[:filters])
             OR u.username ILIKE any (array[:filters]) 
             OR u.display_name ILIKE any (array[:filters])
            )
            AND EXISTS
            (SELECT * FROM tenant WHERE id = data_series_entity_.tenant_id AND reference = :tenant)
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM data_series AS data_series_entity_
            LEFT JOIN "user" u ON data_series_entity_.creator_id = u.id
            WHERE 
            (u.username = :username OR data_series_entity_.sharing_mode <> 'NONE')
            AND (
             data_series_entity_.field_name ILIKE any (array[:filters])
             OR data_series_entity_.data_type ILIKE any (array[:filters]) 
             OR data_series_entity_.display_name ILIKE any (array[:filters])
             OR u.username ILIKE any (array[:filters]) 
             OR u.display_name ILIKE any (array[:filters])
            )
            AND EXISTS
            (SELECT * FROM tenant WHERE id = data_series_entity_.tenant_id AND reference = :tenant)""",

        nativeQuery = true
    )
    suspend fun searchDataSeries(
        tenant: String,
        username: String,
        filters: Collection<String>,
        pageable: Pageable
    ): Page<DataSeriesEntity>

    @Query(
        value =
        """SELECT * 
            FROM data_series AS data_series_entity_
            LEFT JOIN "user" u ON data_series_entity_.creator_id = u.id
            WHERE 
            (u.username = :username OR data_series_entity_.sharing_mode <> 'NONE')
            AND EXISTS
            (SELECT * FROM tenant WHERE id = data_series_entity_.tenant_id AND reference = :tenant)
        """,
        countQuery = """
            SELECT COUNT(*) 
            FROM data_series AS data_series_entity_
            LEFT JOIN "user" u ON data_series_entity_.creator_id = u.id
            WHERE 
            (u.username = :username OR data_series_entity_.sharing_mode <> 'NONE')
            AND EXISTS
            (SELECT * FROM tenant WHERE id = data_series_entity_.tenant_id AND reference = :tenant)
        """,
        nativeQuery = true
    )
    suspend fun searchDataSeries(tenant: String, username: String, pageable: Pageable): Page<DataSeriesEntity>


}