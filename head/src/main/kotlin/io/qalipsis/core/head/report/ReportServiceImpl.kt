package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ReportDataComponentRepository
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import io.qalipsis.core.head.jdbc.repository.ReportRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.DataComponent
import io.qalipsis.core.head.model.DataComponentCreationAndUpdateRequest
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataTable
import io.qalipsis.core.head.model.DataTableCreationAndUpdateRequest
import io.qalipsis.core.head.model.Diagram
import io.qalipsis.core.head.model.DiagramCreationAndUpdateRequest
import io.qalipsis.core.head.model.Report
import io.qalipsis.core.head.model.ReportCreationAndUpdateRequest
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList

/**
 * Default implementation of [ReportService] interface
 *
 * @author Joël Valère
 */

@Singleton
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal class ReportServiceImpl(
    private val reportRepository: ReportRepository,
    private val tenantRepository: TenantRepository,
    private val userRepository: UserRepository,
    private val campaignRepository: CampaignRepository,
    private val campaignScenarioRepository: CampaignScenarioRepository,
    private val reportDataComponentRepository: ReportDataComponentRepository,
    private val dataSeriesRepository: DataSeriesRepository,
    private val idGenerator: IdGenerator
) : ReportService {

    companion object {
        const val REPORT_FETCH_DENY = "This report does not exist in your tenant or you do not have the permission to fetch it"
        const val REPORT_UPDATE_DENY = "This report does not exist in your tenant or you do not have the permission to update it"
        const val REPORT_DELETE_DENY = "This report does not exist in your tenant or you do not have the permission to delete it"
        const val REPORT_DATA_SERIES_NOT_ALLOWED =
            "Some selected data series of your data components cannot be found in your tenant. You can only add data series of your tenant in this report"
        const val REPORT_CAMPAIGN_KEYS_NOT_ALLOWED = "Not all specified campaign keys belong to the tenant"
    }

    override suspend fun get(tenant: String, username: String, reference: String): Report {
        val currentUserId = userRepository.findIdByUsername(username = username)
        val reportEntity = requireNotNull(reportRepository.findByTenantAndReferenceAndCreatorIdOrShare(tenant = tenant, reference = reference, creatorId = currentUserId)) {
            REPORT_FETCH_DENY
        }
        val creatorName = userRepository.findUsernameById(reportEntity.creatorId)
        return if (reportEntity.dataComponents.isNotEmpty()) {
            val dataComponentEntities = reportDataComponentRepository.findByIdInOrderById(reportEntity.dataComponents.map { it.id }).toList()
            toModel(reportEntity.copy(dataComponents = dataComponentEntities), creatorName, tenant)
        }else
            toModel(reportEntity, creatorName, tenant)
    }

    override suspend fun create(
        tenant: String,
        creator: String,
        reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest
    ): Report {
        if (reportCreationAndUpdateRequest.campaignKeys.isNotEmpty()) {
            val existingCampaignKeys = campaignRepository.findKeyByTenantAndKeyIn(tenant, reportCreationAndUpdateRequest.campaignKeys)
            require(existingCampaignKeys == reportCreationAndUpdateRequest.campaignKeys.toSet()) {
                REPORT_CAMPAIGN_KEYS_NOT_ALLOWED
            }
        }
        reportCreationAndUpdateRequest.dataComponents.map {
            checkDataSeriesInDataComponent(tenant, it)
        }
        var createdReport = reportRepository.save(
            ReportEntity(
                reference = idGenerator.short(),
                tenantId = tenantRepository.findIdByReference(tenant),
                creatorId = userRepository.findIdByUsername(creator),
                displayName = reportCreationAndUpdateRequest.displayName,
                sharingMode = reportCreationAndUpdateRequest.sharingMode,
                campaignKeys = reportCreationAndUpdateRequest.campaignKeys,
                campaignNamesPatterns = reportCreationAndUpdateRequest.campaignNamesPatterns,
                scenarioNamesPatterns = reportCreationAndUpdateRequest.scenarioNamesPatterns
            )
        )
        if (reportCreationAndUpdateRequest.dataComponents.isNotEmpty()) {
            createdReport = createdReport.let { saved ->
                saved.copy(
                    dataComponents = reportDataComponentRepository.saveAll(
                        reportCreationAndUpdateRequest.dataComponents.map {
                            toEntity(reportId = saved.id, tenant = tenant, dataComponentCreationAndUpdateRequest = it)
                        }
                    ).toList()
                )
            }
        }
        return toModel(createdReport, creator, tenant)
    }

    override suspend fun update(
        tenant: String,
        username: String,
        reference: String,
        reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest
    ): Report {
        val currentUserId = userRepository.findIdByUsername(username = username)
        val reportEntity = requireNotNull(reportRepository.getReportIfUpdatable(tenant = tenant, reference = reference, currentUserId)){
            REPORT_UPDATE_DENY
        }
        val creatorName = userRepository.findUsernameById(reportEntity.creatorId)

        if (reportCreationAndUpdateRequest.campaignKeys.isNotEmpty()) {
            val existingCampaignKeys = campaignRepository.findKeyByTenantAndKeyIn(tenant, reportCreationAndUpdateRequest.campaignKeys)
            require(existingCampaignKeys == reportCreationAndUpdateRequest.campaignKeys.toSet()) {
                REPORT_CAMPAIGN_KEYS_NOT_ALLOWED
            }
        }
        return if (isUpdateRequired(reportCreationAndUpdateRequest, reportEntity)) {
            reportCreationAndUpdateRequest.dataComponents.map {
                checkDataSeriesInDataComponent(tenant, it)
            }
            reportDataComponentRepository.deleteByReportId(reportEntity.id)
            var updatedReport = reportRepository.update(
                reportEntity.copy(
                    tenantId = reportEntity.tenantId,
                    displayName = reportCreationAndUpdateRequest.displayName,
                    sharingMode = reportCreationAndUpdateRequest.sharingMode,
                    campaignKeys = reportCreationAndUpdateRequest.campaignKeys,
                    campaignNamesPatterns = reportCreationAndUpdateRequest.campaignNamesPatterns,
                    scenarioNamesPatterns = reportCreationAndUpdateRequest.scenarioNamesPatterns,
                    dataComponents = emptyList()
                )
            )
            if (reportCreationAndUpdateRequest.dataComponents.isNotEmpty()) {
                updatedReport = updatedReport.let { updated ->
                    val reportDataComponentEntities = reportCreationAndUpdateRequest.dataComponents.map {
                        toEntity(reportId = updated.id, tenant = tenant, dataComponentCreationAndUpdateRequest = it)
                    }
                    val dataComponents = reportDataComponentRepository.saveAll(
                        reportDataComponentEntities
                    ).toList()
                    updated.copy(
                        dataComponents = dataComponents
                    )
                }
            }
            toModel(updatedReport, creatorName, tenant)
        } else {
            toModel(reportEntity, creatorName, tenant)
        }
    }

    override suspend fun delete(tenant: String, username: String, reference: String) {
        val currentUserId = userRepository.findIdByUsername(username = username)
        val reportEntity = requireNotNull(reportRepository.getReportIfUpdatable(tenant = tenant, reference = reference, currentUserId)){
            REPORT_DELETE_DENY
        }
        reportRepository.delete(reportEntity)
    }

    /**
     * Converts a report entity instance to report model instance.
     */
    private suspend fun toModel(reportEntity: ReportEntity, creator: String, tenant: String): Report {
        val resolvedCampaignKeys =
            if (reportEntity.campaignNamesPatterns.isNotEmpty())
                campaignRepository.findKeysByTenantAndNamePatterns(
                    tenant,
                    reportEntity.campaignNamesPatterns.map {
                        it.replace("*", "%").replace("?", "_")
                    }
                )
            else emptyList()
        val campaignKeysUnion = reportEntity.campaignKeys.plus(resolvedCampaignKeys).distinct()
        val resolvedScenarioNames =
            if (reportEntity.scenarioNamesPatterns.isEmpty())
                campaignScenarioRepository.findNameByCampaignKeys(campaignKeysUnion)
            else
                campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                    reportEntity.scenarioNamesPatterns.map {
                        it.replace("*", "%").replace("?", "_")
                    },
                    campaignKeysUnion
                )
        return Report(
            reference = reportEntity.reference,
            version = reportEntity.version,
            creator = creator,
            displayName = reportEntity.displayName,
            sharingMode = reportEntity.sharingMode,
            campaignKeys = reportEntity.campaignKeys.toList(),
            campaignNamesPatterns = reportEntity.campaignNamesPatterns.toList(),
            resolvedCampaignKeys = resolvedCampaignKeys.toList(),
            scenarioNamesPatterns = reportEntity.scenarioNamesPatterns.toList(),
            resolvedScenarioNames = resolvedScenarioNames,
            dataComponents = if (reportEntity.dataComponents.isNotEmpty())
                reportEntity.dataComponents.map { toModel(it) } else emptyList()

        )
    }


    /**
     * Converts a data component entity instance to data component model instance.
     */
    private suspend fun toModel(dataComponentEntity: ReportDataComponentEntity): DataComponent {

        return if (dataComponentEntity.type == Diagram.TYPE) {
            Diagram(datas = dataComponentEntity.dataSeries.map { DataSeries(it, userRepository.findUsernameById(it.creatorId))})
        } else {
            DataTable(datas = dataComponentEntity.dataSeries.map {DataSeries(it, userRepository.findUsernameById(it.creatorId))})
        }
    }

    /**
     * Converts a data component entity instance to data component creation and update model instance.
     */
    private fun toUpdateRequest(dataComponentEntity: ReportDataComponentEntity): DataComponentCreationAndUpdateRequest {

        return if (dataComponentEntity.type == Diagram.TYPE) {
            DiagramCreationAndUpdateRequest(dataSeriesReferences = dataComponentEntity.dataSeries.map { it.reference })
        } else {
            DataTableCreationAndUpdateRequest(dataSeriesReferences = dataComponentEntity.dataSeries.map { it.reference })
        }
    }

    /**
     * Check if update is require or not buy
     *
     * @param updateRequest the [ReportCreationAndUpdateRequest] instance set by user, for the update.
     * @param reportEntity existing report entity in database.
     */
    private fun isUpdateRequired(updateRequest: ReportCreationAndUpdateRequest, reportEntity: ReportEntity): Boolean {
        return ReportCreationAndUpdateRequest(
            displayName = reportEntity.displayName,
            sharingMode = reportEntity.sharingMode,
            campaignKeys = reportEntity.campaignKeys.toList(),
            campaignNamesPatterns = reportEntity.campaignNamesPatterns.toList(),
            scenarioNamesPatterns = reportEntity.scenarioNamesPatterns.toList(),
            dataComponents = reportEntity.dataComponents.map { toUpdateRequest(it) }
        ) != updateRequest
    }


    /**
     * Converts a data component instance to entity.
     */
    private suspend fun toEntity(
        reportId: Long,
        tenant: String,
        dataComponentCreationAndUpdateRequest: DataComponentCreationAndUpdateRequest
    ): ReportDataComponentEntity {
        return if (dataComponentCreationAndUpdateRequest.type == Diagram.TYPE) {
            val diagram = dataComponentCreationAndUpdateRequest as DiagramCreationAndUpdateRequest
            toDataComponentEntity(reportId, Diagram.TYPE, tenant, diagram.dataSeriesReferences)
        } else {
            val dataTable = dataComponentCreationAndUpdateRequest as DataTableCreationAndUpdateRequest
            toDataComponentEntity(reportId, DataTable.TYPE, tenant, dataTable.dataSeriesReferences)
        }
    }

    /**
     * Checks if all data series in a data component exist and belong to the tenant before saving the associate report.
     */
    private suspend fun checkDataSeriesInDataComponent(
        tenant: String,
        dataComponentCreationAndUpdateRequest: DataComponentCreationAndUpdateRequest
    ): Boolean {
        if (dataComponentCreationAndUpdateRequest.type == Diagram.TYPE) {
            val diagram = dataComponentCreationAndUpdateRequest as DiagramCreationAndUpdateRequest
            diagram.dataSeriesReferences.map {
                require(dataSeriesRepository.checkExistenceByTenantAndReference(tenant = tenant, reference = it)) {
                    REPORT_DATA_SERIES_NOT_ALLOWED
                }
            }
        } else {
            val dataTable = dataComponentCreationAndUpdateRequest as DataTableCreationAndUpdateRequest
            dataTable.dataSeriesReferences.map {
                require(dataSeriesRepository.checkExistenceByTenantAndReference(tenant = tenant, reference = it)) {
                    REPORT_DATA_SERIES_NOT_ALLOWED
                }
            }
        }
        return true
    }

    /**
     * Build a data component entity instance for a list of data series and type of component
     */
    private suspend fun toDataComponentEntity(
        reportId: Long,
        type: DataComponentType,
        tenant: String,
        dataSeries: List<String>
    ): ReportDataComponentEntity {
        val dataSeriesEntities = dataSeries.map {
            dataSeriesRepository.findByReferenceAndTenant(it, tenant)
        }
        return ReportDataComponentEntity(reportId = reportId, type = type, dataSeries = dataSeriesEntities)
    }
}