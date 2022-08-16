package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
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

}