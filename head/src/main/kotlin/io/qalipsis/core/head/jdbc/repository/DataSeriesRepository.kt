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

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Expandable
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.Defaults

/**
 * Micronaut data repository to operate with [DataSeriesEntity].
 *
 * @author Palina Bril
 */
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface DataSeriesRepository : CoroutineCrudRepository<DataSeriesEntity, Long> {

    @Query(
        """SELECT count(*) > 0
            FROM data_series
            WHERE id <> :id AND display_name = :displayName AND EXISTS (SELECT 1 FROM tenant WHERE data_series.tenant_id = tenant.id AND tenant.reference = :tenant)"""
    )
    suspend fun existsByTenantReferenceAndDisplayNameAndIdNot(
        tenant: String,
        displayName: String,
        id: Long = -1
    ): Boolean

    @Query(
        """SELECT *
            FROM data_series
            WHERE reference = :reference AND EXISTS (SELECT 1 FROM tenant WHERE data_series.tenant_id = tenant.id AND tenant.reference = :tenant)"""
    )
    suspend fun findByTenantAndReference(tenant: String, reference: String): DataSeriesEntity

    @Query(
        """SELECT 
            data_series.id, 
            data_series.reference, 
            data_series.version, 
            data_series.tenant_id, 
            data_series.display_name, 
            data_series.data_type,
            data_series.value_name,
            data_series.color, 
            data_series.filters, 
            data_series.field_name, 
            data_series.aggregation_operation, 
            data_series.timeframe_unit_ms, 
            data_series.display_format,
            data_series.query, 
            data_series.color_opacity,
                CASE 
                    WHEN t.reference <> :tenant THEN 'READONLY'
                    ELSE sharing_mode
                END AS sharing_mode,
                CASE 
                    WHEN t.reference <> :tenant THEN -1
                    ELSE creator_id
                END AS creator_id
                FROM data_series data_series
                    JOIN tenant t ON data_series.tenant_id = t.id
                WHERE data_series.reference in (:references) AND t.reference IN (:tenant, '${Defaults.TENANT}')
            """
    )
    suspend fun findAllByTenantAndReferences(
        tenant: String,
        references: Collection<String>
    ): List<DataSeriesEntity>

    @Query(
        value = """SELECT 
                    data_series_entity_.id, 
                    data_series_entity_.reference, 
                    data_series_entity_.version, 
                    data_series_entity_.tenant_id, 
                    data_series_entity_.display_name, 
                    data_series_entity_.data_type,
                    data_series_entity_.value_name,
                    data_series_entity_.color, 
                    data_series_entity_.filters, 
                    data_series_entity_.field_name, 
                    data_series_entity_.aggregation_operation, 
                    data_series_entity_.timeframe_unit_ms, 
                    data_series_entity_.display_format,
                    data_series_entity_.query, 
                    data_series_entity_.color_opacity,
                        CASE 
                            WHEN t.id <> current_tenant.id THEN 'READONLY'
                            ELSE sharing_mode
                        END AS sharing_mode,
                        CASE 
                            WHEN t.id <> current_tenant.id THEN -1
                            ELSE creator_id
                        END AS creator_id 
                        FROM data_series AS data_series_entity_
                            LEFT JOIN "user" u ON data_series_entity_.creator_id = u.id
                            JOIN tenant t ON data_series_entity_.tenant_id = t.id
                            JOIN tenant current_tenant ON current_tenant.reference = :currentTenant
                        WHERE 
                        (u.username = :username OR data_series_entity_.sharing_mode <> 'NONE')
                        AND (
                         data_series_entity_.field_name ILIKE any (array[:filters]) 
                         OR data_series_entity_.data_type ILIKE any (array[:filters])
                         OR data_series_entity_.display_name ILIKE any (array[:filters])
                         OR u.username ILIKE any (array[:filters]) 
                         OR u.display_name ILIKE any (array[:filters])
                        )
                        AND t.reference IN (:tenant, '${Defaults.TENANT}')
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
            (SELECT * FROM tenant t WHERE t.id = data_series_entity_.tenant_id AND t.reference IN (:tenant, :currentTenant, '${Defaults.TENANT}'))""",
        nativeQuery = true
    )
    suspend fun searchDataSeries(
        tenant: String,
        username: String,
        currentTenant: String = tenant,
        @Expandable filters: Collection<String>,
        pageable: Pageable
    ): Page<DataSeriesEntity>

    @Query(
        value =
        """SELECT 
            data_series_entity_.id, 
            data_series_entity_.reference, 
            data_series_entity_.version, 
            data_series_entity_.tenant_id, 
            data_series_entity_.display_name, 
            data_series_entity_.data_type,
            data_series_entity_.value_name,
            data_series_entity_.color, 
            data_series_entity_.filters, 
            data_series_entity_.field_name, 
            data_series_entity_.aggregation_operation, 
            data_series_entity_.timeframe_unit_ms, 
            data_series_entity_.display_format,
            data_series_entity_.query, 
            data_series_entity_.color_opacity,
                CASE 
                    WHEN t.id <> current_tenant.id THEN 'READONLY'
                    ELSE sharing_mode
                END AS sharing_mode,
                CASE 
                    WHEN t.id <> current_tenant.id THEN -1
                    ELSE creator_id
                END AS creator_id 
                FROM data_series AS data_series_entity_
                    LEFT JOIN "user" u ON data_series_entity_.creator_id = u.id
                    JOIN tenant t ON data_series_entity_.tenant_id = t.id
                    JOIN tenant current_tenant ON current_tenant.reference = :currentTenant
                WHERE 
                (u.username = :username OR data_series_entity_.sharing_mode <> 'NONE')
                AND t.reference IN (:tenant, '${Defaults.TENANT}')""",
        countQuery = """
            SELECT COUNT(*) 
            FROM data_series AS data_series_entity_
            LEFT JOIN "user" u ON data_series_entity_.creator_id = u.id
            WHERE 
            (u.username = :username OR data_series_entity_.sharing_mode <> 'NONE')
            AND EXISTS
            (SELECT * FROM tenant t WHERE t.id = data_series_entity_.tenant_id AND t.reference IN (:tenant, :currentTenant, '${Defaults.TENANT}'))""",
        nativeQuery = true
    )
    suspend fun searchDataSeries(
        tenant: String,
        username: String,
        currentTenant: String = tenant,
        pageable: Pageable
    ): Page<DataSeriesEntity>

    @Query(
        value = """SELECT tenant_id, * FROM data_series ORDER BY tenant_id""",
        countQuery = """SELECT COUNT(*) FROM data_series""",
        nativeQuery = true
    )
    suspend fun findAll(pageable: Pageable): Page<DataSeriesEntity>

    @Query(
        """SELECT 
                CASE 
                    WHEN EXISTS 
                        (SELECT 1 FROM data_series 
                            WHERE reference = :reference AND EXISTS (SELECT 1 FROM tenant WHERE data_series.tenant_id = tenant.id AND tenant.reference = :tenant)) 
                    THEN TRUE 
                    ELSE FALSE 
                END"""
    )
    suspend fun checkExistenceByTenantAndReference(tenant: String, reference: String): Boolean

}