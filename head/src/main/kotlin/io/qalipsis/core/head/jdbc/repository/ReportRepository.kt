package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.ReportEntity

/**
 * Micronaut data repository to operate with [ReportEntity].
 *
 * @author Joël Valère
 */

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface ReportRepository : CoroutineCrudRepository<ReportEntity, Long> {

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
        WHERE EXISTS (SELECT 1 FROM tenant WHERE report_entity_.tenant_id = tenant.id AND tenant.reference = :tenant) 
            AND report_entity_."reference" = :reference
        ORDER BY report_entity_data_components_."id""""
    )
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    suspend fun findByTenantAndReference(tenant: String, reference: String): ReportEntity

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
     * The report is returns only if the creator is the referred or if the report is shared (different from NONE)
     */
    @Query(
        """SELECT 
            report_entity_."id",
            report_entity_."version",
            report_entity_."reference",
            report_entity_."tenant_id",
            report_entity_."creator_id",
            report_entity_."display_name",
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
    suspend fun findByTenantAndReferenceAndCreatorIdOrShare(tenant: String, reference: String, creatorId: Long): ReportEntity?

    /**
     * Find a report in a tenant by its reference.
     * The report is returns only if the creator is the referred or if the report is shared in WRITE
     */
    @Query(
        """SELECT 
            report_entity_."id",
            report_entity_."version",
            report_entity_."reference",
            report_entity_."tenant_id",
            report_entity_."creator_id",
            report_entity_."display_name",
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

    @Query(
        value = """
        SELECT distinct 
            report_entity_."id",
            report_entity_."version",
            report_entity_."reference",
            report_entity_."tenant_id",
            report_entity_."creator_id",
            report_entity_."display_name",
            report_entity_."sharing_mode",
            report_entity_."campaign_keys",
            report_entity_."campaign_names_patterns",
            report_entity_."scenario_names_patterns",
            report_entity_."query",
            report_data_component_entity_."id" AS data_components_id,
            report_data_component_entity_."type" AS data_components_type,
            report_data_component_entity_."report_id" AS data_components_report_id 
        FROM "report" report_entity_ 
        LEFT JOIN "user" user_entity_ 
            ON report_entity_.creator_id = user_entity_.id
        LEFT JOIN "data_component" report_data_component_entity_ 
            ON report_entity_."id" = report_data_component_entity_."report_id"
        LEFT JOIN "data_component_data_series" data_component_data_series_entity_ 
            ON report_data_component_entity_."id" = data_component_data_series_entity_."data_component_id"  
        LEFT JOIN "data_series" data_series_entity_ 
            ON data_component_data_series_entity_."data_series_id" = data_series_entity_."id" 
        WHERE (
                user_entity_.username = :username 
                OR report_entity_.sharing_mode <> 'NONE'
            )
            AND (
                report_entity_.display_name ILIKE any (array[:filters])
                OR user_entity_.username ILIKE any (array[:filters]) 
                OR user_entity_.display_name ILIKE any (array[:filters])
                OR data_series_entity_.display_name ILIKE any (array[:filters])
            )
            AND EXISTS (SELECT * FROM tenant WHERE id = report_entity_.tenant_id AND tenant.reference = :tenant)
        """,
        countQuery = """
        SELECT distinct COUNT(*)
        FROM "report" report_entity_ 
        LEFT JOIN "user" user_entity_ 
            ON report_entity_.creator_id = user_entity_.id
        LEFT JOIN "data_component" report_data_component_entity_ 
            ON report_entity_."id" = report_data_component_entity_."report_id"
        LEFT JOIN "data_component_data_series" data_component_data_series_entity_ 
            ON report_data_component_entity_."id" = data_component_data_series_entity_."data_component_id"  
        LEFT JOIN "data_series" data_series_entity_ 
            ON data_component_data_series_entity_."data_series_id" = data_series_entity_."id" 
        WHERE (
                user_entity_.username = :username 
                OR report_entity_.sharing_mode <> 'NONE'
            )
            AND (
                report_entity_.display_name ILIKE any (array[:filters])
                OR user_entity_.username ILIKE any (array[:filters]) 
                OR user_entity_.display_name ILIKE any (array[:filters])
                OR data_series_entity_.display_name ILIKE any (array[:filters])
            )
            AND EXISTS (SELECT * FROM tenant WHERE id = report_entity_.tenant_id AND tenant.reference = :tenant)
        """,
        nativeQuery = true
    )
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    suspend fun searchReports(tenant: String, username: String, filters: Collection<String>, pageable: Pageable): Page<ReportEntity>

    @Query(
        value = """
        SELECT distinct
            report_entity_."id",
            report_entity_."version",
            report_entity_."reference",
            report_entity_."tenant_id",
            report_entity_."creator_id",
            report_entity_."display_name",
            report_entity_."sharing_mode",
            report_entity_."campaign_keys",
            report_entity_."campaign_names_patterns",
            report_entity_."scenario_names_patterns",
            report_entity_."query",
            report_data_component_entity_."id" AS data_components_id,
            report_data_component_entity_."type" AS data_components_type,
            report_data_component_entity_."report_id" AS data_components_report_id 
        FROM "report" report_entity_ 
        LEFT JOIN "user" user_entity_ 
            ON report_entity_.creator_id = user_entity_.id
        LEFT JOIN "data_component" report_data_component_entity_ 
            ON report_entity_."id" = report_data_component_entity_."report_id"
        LEFT JOIN "data_component_data_series" data_component_data_series_entity_ 
            ON report_data_component_entity_."id" = data_component_data_series_entity_."data_component_id"  
        LEFT JOIN "data_series" data_series_entity_ 
            ON data_component_data_series_entity_."data_series_id" = data_series_entity_."id" 
        WHERE (
                user_entity_.username = :username 
                OR report_entity_.sharing_mode <> 'NONE'
            )
            AND EXISTS (SELECT * FROM tenant WHERE id = report_entity_.tenant_id AND tenant.reference = :tenant)
        """,
        countQuery = """
        SELECT distinct COUNT(*)
        FROM "report" report_entity_ 
        LEFT JOIN "user" user_entity_ 
            ON report_entity_.creator_id = user_entity_.id
        LEFT JOIN "data_component" report_data_component_entity_ 
            ON report_entity_."id" = report_data_component_entity_."report_id"
        LEFT JOIN "data_component_data_series" data_component_data_series_entity_ 
            ON report_data_component_entity_."id" = data_component_data_series_entity_."data_component_id"  
        LEFT JOIN "data_series" data_series_entity_ 
            ON data_component_data_series_entity_."data_series_id" = data_series_entity_."id" 
        WHERE (
                user_entity_.username = :username 
                OR report_entity_.sharing_mode <> 'NONE'
            )
            AND EXISTS (SELECT * FROM tenant WHERE id = report_entity_.tenant_id AND tenant.reference = :tenant)
        """,
        nativeQuery = true
    )
    @Join(value = "dataComponents", type = Join.Type.LEFT_FETCH)
    suspend fun searchReports(tenant: String, username: String, pageable: Pageable): Page<ReportEntity>
}