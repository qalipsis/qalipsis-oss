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
        """SELECT *
            FROM data_series
            WHERE reference in (:references) AND EXISTS (SELECT 1 FROM tenant WHERE data_series.tenant_id = tenant.id AND tenant.reference = :tenant)"""
    )
    suspend fun findAllByTenantAndReferences(
        tenant: String,
        references: Collection<String>
    ): List<DataSeriesEntity>

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
        @Expandable filters: Collection<String>,
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