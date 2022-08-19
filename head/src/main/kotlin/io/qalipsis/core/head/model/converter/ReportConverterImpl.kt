package io.qalipsis.core.head.model.converter

import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ReportDataComponentRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.DataComponent
import io.qalipsis.core.head.model.DataTable
import io.qalipsis.core.head.model.Diagram
import io.qalipsis.core.head.model.Report
import jakarta.inject.Singleton

/**
 * Convertor for different formats around the reports.
 *
 * @author Joël Valère
 */
@Singleton
internal class ReportConverterImpl(
    private val campaignRepository: CampaignRepository,
    private val campaignScenarioRepository: CampaignScenarioRepository,
    private val dataSeriesConverter: DataSeriesConverter,
    private val reportDataComponentRepository: ReportDataComponentRepository,
    private val userRepository: UserRepository
): ReportConverter {

    override suspend fun convertToModel(reportEntity: ReportEntity): Report {
        val dataComponentEntities = if (reportEntity.dataComponents.isNotEmpty()) {
            reportDataComponentRepository.findByIdInOrderById(reportEntity.dataComponents.map { it.id }).toList()
        } else emptyList()

        val resolvedCampaignKeys = if (reportEntity.campaignNamesPatterns.isNotEmpty())
            campaignRepository.findKeysByTenantIdAndNamePatterns(
                reportEntity.tenantId,
                reportEntity.campaignNamesPatterns.map {
                    it.replace("*", "%").replace("?", "_")
                }
            )
        else emptyList()
        val campaignKeysUnion = reportEntity.campaignKeys.plus(resolvedCampaignKeys).distinct()
        val resolvedScenarioNames =
            if (campaignKeysUnion.isNotEmpty())
                if (reportEntity.scenarioNamesPatterns.isEmpty())
                    campaignScenarioRepository.findNameByCampaignKeys(campaignKeysUnion)
                else
                    campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                        reportEntity.scenarioNamesPatterns.map {
                            it.replace("*", "%").replace("?", "_")
                        },
                        campaignKeysUnion
                    )
            else emptyList()
        return Report(
            reference = reportEntity.reference,
            version = reportEntity.version,
            creator = userRepository.findUsernameById(reportEntity.creatorId),
            displayName = reportEntity.displayName,
            sharingMode = reportEntity.sharingMode,
            campaignKeys = reportEntity.campaignKeys.toList(),
            campaignNamesPatterns = reportEntity.campaignNamesPatterns.toList(),
            resolvedCampaignKeys = resolvedCampaignKeys.toList(),
            scenarioNamesPatterns = reportEntity.scenarioNamesPatterns.toList(),
            resolvedScenarioNames = resolvedScenarioNames,
            dataComponents = if(dataComponentEntities.isNotEmpty())
                dataComponentEntities.map { toModel(it) } else emptyList()
        )
    }

    /**
     * Converts a [ReportDataComponentEntity] instance to [DataComponent] instance.
     */
    suspend fun toModel(dataComponentEntity: ReportDataComponentEntity): DataComponent {
        return if (dataComponentEntity.type == Diagram.TYPE) {
            Diagram(datas = dataComponentEntity.dataSeries.map { dataSeriesConverter.convertToModel(it) })
        } else {
            DataTable(datas = dataComponentEntity.dataSeries.map { dataSeriesConverter.convertToModel(it) })
        }
    }
}