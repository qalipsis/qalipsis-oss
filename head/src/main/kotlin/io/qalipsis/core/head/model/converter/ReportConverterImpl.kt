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
    private val reportDataComponentRepository: ReportDataComponentRepository,
    private val userRepository: UserRepository
) : ReportConverter {

    override suspend fun convertToModel(reportEntity: ReportEntity): Report {
        val dataComponentEntities = if (reportEntity.dataComponents.isNotEmpty()) {
            reportDataComponentRepository.findByIdInOrderById(reportEntity.dataComponents.map { it.id }).toList()
        } else emptyList()

        val resolvedCampaignKeysAndNames = if (reportEntity.campaignNamesPatterns.isNotEmpty())
            campaignRepository.findKeysAndNamesByTenantIdAndNamePatternsOrKeys(
                reportEntity.tenantId,
                reportEntity.campaignNamesPatterns.map {
                    it.replace("*", "%").replace("?", "_")
                },
                reportEntity.campaignKeys
            )
        else emptyList()
        val resolvedCampaignKeys = resolvedCampaignKeysAndNames.map { it.key }
        val campaignKeysUnion = reportEntity.campaignKeys.plus(resolvedCampaignKeys).distinct()
        val resolvedScenarioNames =
            if (campaignKeysUnion.isNotEmpty())
                if (reportEntity.scenarioNamesPatterns.isEmpty())
                    campaignScenarioRepository.findNameByCampaignKeys(reportEntity.tenantId, campaignKeysUnion)
                else
                    campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(
                        reportEntity.tenantId,
                        reportEntity.scenarioNamesPatterns.map {
                            it.replace("*", "%").replace("?", "_")
                        },
                        campaignKeysUnion
                    )
            else emptyList()
        return Report(
            reference = reportEntity.reference,
            version = reportEntity.version,
            creator = userRepository.findUsernameById(reportEntity.creatorId) ?: "",
            displayName = reportEntity.displayName,
            sharingMode = reportEntity.sharingMode,
            campaignKeys = reportEntity.campaignKeys.toList(),
            campaignNamesPatterns = reportEntity.campaignNamesPatterns.toList(),
            resolvedCampaigns = resolvedCampaignKeysAndNames.toList(),
            scenarioNamesPatterns = reportEntity.scenarioNamesPatterns.toList(),
            resolvedScenarioNames = resolvedScenarioNames,
            dataComponents = if (dataComponentEntities.isNotEmpty())
                dataComponentEntities.map { toModel(it) } else emptyList()
        )
    }

    /**
     * Converts a [ReportDataComponentEntity] instance to [DataComponent] instance.
     */
    suspend fun toModel(dataComponentEntity: ReportDataComponentEntity): DataComponent {
        return if (dataComponentEntity.type == Diagram.TYPE) {
            Diagram(datas = dataComponentEntity.dataSeries.map { it.toModel("") })
        } else {
            DataTable(datas = dataComponentEntity.dataSeries.map { it.toModel("") })
        }
    }
}