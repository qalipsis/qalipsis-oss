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
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.ReportEntity

/**
 * Micronaut data repository to operate with [ReportEntity].
 *
 * @author Joël Valère
 */

@R2dbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
interface ReportRepository : CoroutineCrudRepository<ReportEntity, Long> {


    /**
     * Find a list of report based on their internal IDs.
     *
     * The details of the [io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity] should be
     * fetched separately using [ReportDataComponentRepository.findByIdInOrderById] once the report was fetched.
     */
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    fun findByIdIn(ids: Collection<Long>): Collection<ReportEntity>

    /**
     * Find a report in a tenant by its reference.
     *
     * The details of the [io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity] should be
     * fetched separately using [ReportDataComponentRepository.findByIdInOrderById].
     */

    @Query(
        """SELECT
            report_entity_."id",
            report_entity_."version",
            report_entity_."reference",
            report_entity_."tenant_id",
            report_entity_."creator_id",
            report_entity_."display_name",
            report_entity_."description",
            report_entity_."sharing_mode",
            report_entity_."campaign_keys",
            report_entity_."campaign_names_patterns",
            report_entity_."scenario_names_patterns",
            report_entity_."query",
            report_entity_data_components_."id" AS data_components_id,
            report_entity_data_components_."type" AS data_components_type,
            report_entity_data_components_."report_id" AS data_components_report_id,
            data_series_."id" AS data_components_data_series_id,
            data_series_."reference" AS data_components_data_series_reference,
            data_series_."version" AS data_components_data_series_version,
            data_series_."tenant_id" AS data_components_data_series_tenant_id,
            data_series_."creator_id" AS data_components_data_series_creator_id,
            data_series_."display_name" AS data_components_data_series_display_name,
            data_series_."sharing_mode" AS data_components_data_series_sharing_mode,
            data_series_."data_type" AS data_components_data_series_data_type,
            data_series_."value_name" AS data_components_data_series_value_name,
            data_series_."color" AS data_components_data_series_color,
            data_series_."filters" AS data_components_data_series_filters,
            data_series_."field_name" AS data_components_data_series_field_name,
            data_series_."aggregation_operation" AS data_components_data_series_aggregation_operation,
            data_series_."timeframe_unit_ms" AS data_components_data_series_timeframe_unit_ms,
            data_series_."display_format" AS data_components_data_series_display_format,
            data_series_."query" AS data_components_data_series_query,
            data_series_."color_opacity" AS data_components_data_series_color_opacity
        FROM "report" report_entity_
        LEFT JOIN "data_component" report_entity_data_components_
            ON report_entity_."id" = report_entity_data_components_."report_id"
        LEFT JOIN "data_component_data_series" report_entity_data_component_data_series_
            ON report_entity_data_components_."id" = report_entity_data_component_data_series_."data_component_id"
        LEFT JOIN "data_series" data_series_
            ON report_entity_data_component_data_series_."data_series_id" = data_series_."id"
        WHERE EXISTS (SELECT 1 FROM tenant WHERE report_entity_.tenant_id = tenant.id AND tenant.reference = :tenant)
            AND report_entity_."reference" = :reference
        ORDER BY report_entity_data_components_."id""""
    )
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    @Join(value = "dataComponents.dataSeries", type = Join.Type.LEFT_FETCH)
    suspend fun findByTenantAndReference(tenant: String, reference: String): ReportEntity

    @Query(
        """SELECT count(*) > 0
            FROM report
            WHERE id <> :id AND display_name = :displayName AND EXISTS (SELECT 1 FROM tenant WHERE report.tenant_id = tenant.id AND tenant.reference = :tenant)"""
    )
    suspend fun existsByTenantReferenceAndDisplayNameAndIdNot(
        tenant: String,
        displayName: String,
        id: Long = -1
    ): Boolean

    /**
     * Find a report by its internal ID.
     *
     * The details of the [io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity] should be
     * fetched separately using [ReportDataComponentRepository.findByIdInOrderById] once the report was fetched.
     */
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    override suspend fun findById(id: Long): ReportEntity?

    /**
     * Find a report in a tenant by its reference.
     * The report is returned only if the creator is the referred or if the report is shared (different from NONE)
     */
    @Query(
        """SELECT 
            report_entity_."id",
            report_entity_."version",
            report_entity_."reference",
            report_entity_."tenant_id",
            report_entity_."creator_id",
            report_entity_."display_name",
            report_entity_."description",
            report_entity_."sharing_mode",
            report_entity_."campaign_keys",
            report_entity_."campaign_names_patterns",
            report_entity_."scenario_names_patterns",
            report_entity_."query",
            report_entity_data_components_."id" AS data_components_id,
            report_entity_data_components_."type" AS data_components_type,
            report_entity_data_components_."report_id" AS data_components_report_id 
        FROM "report" report_entity_ 
        LEFT JOIN "data_component" report_entity_data_components_ 
            ON report_entity_."id" = report_entity_data_components_."report_id"
        WHERE EXISTS (SELECT 1 FROM tenant WHERE report_entity_."tenant_id" = tenant.id AND tenant.reference = :tenant) 
            AND report_entity_."reference" = :reference
            AND (report_entity_."creator_id" = :creatorId OR report_entity_."sharing_mode" <> 'NONE')
        ORDER BY report_entity_data_components_."id""""
    )
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    suspend fun findByTenantAndReferenceAndCreatorIdOrShare(
        tenant: String,
        reference: String,
        creatorId: Long,
    ): ReportEntity?

    /**
     * Find a report in a tenant by its reference.
     * The report is returned only if the creator is the referred or if the report is shared in WRITE
     */
    @Query(
        """SELECT 
            report_entity_."id",
            report_entity_."version",
            report_entity_."reference",
            report_entity_."tenant_id",
            report_entity_."creator_id",
            report_entity_."display_name",
            report_entity_."description",
            report_entity_."sharing_mode",
            report_entity_."campaign_keys",
            report_entity_."campaign_names_patterns",
            report_entity_."scenario_names_patterns",
            report_entity_."query",
            report_entity_data_components_."id" AS data_components_id,
            report_entity_data_components_."type" AS data_components_type,
            report_entity_data_components_."report_id" AS data_components_report_id 
        FROM "report" report_entity_ 
        LEFT JOIN "data_component" report_entity_data_components_ 
            ON report_entity_."id" = report_entity_data_components_."report_id"
        WHERE EXISTS (SELECT 1 FROM tenant WHERE report_entity_."tenant_id" = tenant.id AND tenant.reference = :tenant) 
            AND report_entity_."reference" = :reference
            AND (report_entity_."creator_id" = :creatorId OR report_entity_."sharing_mode" = 'WRITE')
        ORDER BY report_entity_data_components_."id""""
    )
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    suspend fun getReportIfUpdatable(tenant: String, reference: String, creatorId: Long): ReportEntity?

    /**
     * Deletes a list of reports by its unique references.
     */
    @Query("DELETE FROM report WHERE reference IN (:references)")
    suspend fun deleteAllByReference(references: Collection<String>): Int

    /**
     * Find all reports in a tenant by its reference.
     * These reports are returned only if the creator is the referred or if the report is shared in WRITE.
     */
    @Query(
        """
            SELECT
                 report_entity_."reference"
            FROM "report" report_entity_ 
                LEFT JOIN "data_component" report_entity_data_components_ 
                    ON report_entity_."id" = report_entity_data_components_."report_id"
            WHERE EXISTS (SELECT 1 FROM tenant WHERE report_entity_."tenant_id" = tenant.id AND tenant.reference = :tenant) 
                AND report_entity_."reference" IN (:references)
                AND (report_entity_."creator_id" = :creatorId OR report_entity_."sharing_mode" = 'WRITE')
            ORDER BY report_entity_data_components_."id""""
    )
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    suspend fun getUpdatableReportReferences(tenant: String, references: Collection<String>, creatorId: Long): List<String>

    @Query(
        value = """
        SELECT 
            report_entity_.id
        FROM report report_entity_
        WHERE (
                sharing_mode <> 'NONE' 
                OR EXISTS (SELECT 1 FROM "user" WHERE report_entity_.creator_id = "user".id AND "user".username = :username)
            )
            AND (
                report_entity_.display_name ILIKE any (array[:filters])
                OR report_entity_.description ILIKE any (array[:filters])
                OR EXISTS (
                    SELECT 1 FROM "user" 
                    WHERE report_entity_.creator_id = "user".id 
                        AND (
                            "user".username ILIKE any (array[:filters])
                            OR "user".display_name ILIKE any (array[:filters])
                        )
                )
                OR EXISTS (
                    SELECT 1 FROM data_component
                    WHERE report_entity_.id = data_component.report_id
                        AND EXISTS (
                            SELECT 1 FROM data_component_data_series
                            WHERE data_component.id = data_component_data_series.data_component_id
                                AND EXISTS (
                                    SELECT 1 FROM data_series
                                    WHERE data_component_data_series.data_series_id = data_series.id
                                        AND data_series.display_name ILIKE any (array[:filters])
                                )
                        )
                )
            )
            AND EXISTS (SELECT 1 FROM tenant WHERE id = report_entity_.tenant_id AND tenant.reference = :tenant)
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM report
        WHERE (
                sharing_mode <> 'NONE' 
                OR EXISTS (SELECT 1 FROM "user" WHERE report.creator_id = "user".id AND "user".username = :username)
            )
            AND (
                report.display_name ILIKE any (array[:filters])
                OR report.description ILIKE any (array[:filters])
                OR EXISTS (
                    SELECT 1 FROM "user" 
                    WHERE report.creator_id = "user".id 
                        AND (
                            "user".username ILIKE any (array[:filters])
                            OR "user".display_name ILIKE any (array[:filters])
                        )
                )
                OR EXISTS (
                    SELECT 1 FROM data_component
                    WHERE report.id = data_component.report_id
                        AND EXISTS (
                            SELECT 1 FROM data_component_data_series
                            WHERE data_component.id = data_component_data_series.data_component_id
                                AND EXISTS (
                                    SELECT 1 FROM data_series
                                    WHERE data_component_data_series.data_series_id = data_series.id
                                        AND data_series.display_name ILIKE any (array[:filters])
                                )
                        )
                )
            )
            AND EXISTS (SELECT 1 FROM tenant WHERE id = report.tenant_id AND tenant.reference = :tenant)
        """
    )
    suspend fun searchReports(
        tenant: String,
        username: String,
        filters: Collection<String>,
        pageable: Pageable,
    ): Page<Long>

    @Query(
        value = """
        SELECT 
            report_entity_.id
        FROM report report_entity_
        WHERE (
                sharing_mode <> 'NONE' 
                OR EXISTS (SELECT 1 FROM "user" where report_entity_.creator_id = "user".id AND "user".username = :username)
            ) 
            AND EXISTS (SELECT 1 FROM tenant WHERE id = report_entity_.tenant_id AND tenant.reference = :tenant)
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM report
        WHERE (
                sharing_mode <> 'NONE' 
                OR EXISTS (SELECT 1 FROM "user" WHERE report.creator_id = "user".id AND "user".username = :username)
            )
            AND EXISTS (SELECT 1 FROM tenant WHERE id = report.tenant_id AND tenant.reference = :tenant)
        """
    )
    suspend fun searchReports(tenant: String, username: String, pageable: Pageable): Page<Long>

}