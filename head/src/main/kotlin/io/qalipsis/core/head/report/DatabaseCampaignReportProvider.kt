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

package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ExecutionStatus.IN_PROGRESS
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.api.report.ExecutionStatus.SCHEDULED
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.entity.StepReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportRepository
import io.qalipsis.core.head.jdbc.repository.StepReportRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.core.head.model.StepExecutionDetails
import io.qalipsis.core.head.model.Zone
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.zone.ZoneService
import jakarta.inject.Singleton

/**
 * Implementation of [CampaignReportProvider] interface
 *
 * @author Francisca Eze
 */
@Singleton
@Primary
@Requires(beans = [DatabaseCampaignReportPublisher::class])
class DatabaseCampaignReportProvider(
    private val campaignRepository: CampaignRepository,
    private val campaignConverter: CampaignConverter,
    private val campaignScenarioRepository: CampaignScenarioRepository,
    private val campaignReportRepository: CampaignReportRepository,
    private val scenarioReportMessageRepository: ScenarioReportMessageRepository,
    private val scenarioReportRepository: ScenarioReportRepository,
    private val stepReportRepository: StepReportRepository,
    private val campaignService: CampaignService,
    private val zoneService: ZoneService,
    private val campaignMeterEnricher: CampaignMeterEnricher
) : CampaignReportProvider {

    override suspend fun retrieve(tenant: String, campaignKey: CampaignKey): CampaignExecutionDetails =
        retrieveCampaignsReports(tenant, listOf(campaignKey)).firstOrNull()
            ?: error("Campaign $campaignKey not found for tenant $tenant")

    override suspend fun retrieveCampaignsReports(
        tenant: String,
        campaignKeys: Collection<CampaignKey>
    ): Collection<CampaignExecutionDetails> {
        val campaignEntities = campaignRepository.findByTenantAndKeys(tenant, campaignKeys)
        return supplyIf(campaignEntities.isNotEmpty()) {
            val campaigns = campaignEntities.map { campaignConverter.convertToModel(it) }
            val campaignByEntityId = campaignEntities.zip(campaigns)
                .associate { (entity, campaign) -> entity.id to campaign }

            val completedEntityIds = campaignEntities
                .filter { campaignByEntityId[it.id]!!.status !in RUNNING_STATUSES }
                .map { it.id }

            val allReports = fetchReports(completedEntityIds)
            val reportByCampaignId = allReports.associateBy { it.campaignId }
            val scenariosFromReports = fetchScenarioEntities(allReports)

            val ongoingEntityIds = campaignEntities.map { it.id }.filterNot { reportByCampaignId.containsKey(it) }
            val ongoingScenariosByCampaignId = fetchOngoingScenarios(ongoingEntityIds)

            val scenarioLookup = buildScenarioLookup(scenariosFromReports)

            val completedCampaigns = campaigns.filter { it.status !in RUNNING_STATUSES }
            val (configByCampaignKey, resolvedZonesByKey) = resolveZonesAndConfigs(tenant, completedCampaigns)
            val metersByCampaignKey =
                fetchMeters(tenant, completedCampaigns, scenariosFromReports.map { it.name }.distinct())

            campaignEntities.map { campaignEntity ->
                val campaign = campaignByEntityId[campaignEntity.id]!!
                val campaignReport = reportByCampaignId[campaignEntity.id]
                buildCampaignReport(
                    campaign = campaign,
                    campaignReport = campaignReport,
                    scenarioLookup = scenarioLookup,
                    ongoingScenarios = ongoingScenariosByCampaignId[campaignEntity.id].orEmpty(),
                    config = configByCampaignKey[campaign.key],
                    resolvedZonesByKey = resolvedZonesByKey,
                    meterDistribution = metersByCampaignKey[campaign.key] ?: MeterDistribution(
                        emptyList(),
                        emptyMap(),
                        emptyMap()
                    )
                )
            }
        }.orEmpty()
    }

    /** Skips the repository call entirely when there are no completed campaign IDs to avoid an empty-IN query. */
    private suspend fun fetchReports(completedEntityIds: List<Long>): List<CampaignReportEntity> =
        supplyIf(completedEntityIds.isNotEmpty()) {
            campaignReportRepository.findByCampaignIdIn(completedEntityIds)
        }.orEmpty()

    /**
     * Prefers scenario reports already embedded in the campaign report entity; falls back to the repository only
     * when the embedded list is empty, which happens for older records persisted before eager loading was added.
     */
    private suspend fun fetchScenarioEntities(allReports: List<CampaignReportEntity>): List<ScenarioReportEntity> =
        allReports.flatMap { report ->
            report.scenariosReports.ifEmpty {
                scenarioReportRepository.findByCampaignReportIdIn(listOf(report.id))
            }
        }

    /** Returns an empty map when there are no ongoing entity IDs to avoid an empty-IN query. */
    private suspend fun fetchOngoingScenarios(ongoingEntityIds: List<Long>): Map<Long, Collection<CampaignScenarioEntity>> =
        if (ongoingEntityIds.isNotEmpty()) {
            campaignScenarioRepository.findByCampaignIdIn(ongoingEntityIds).groupBy { it.campaignId }
        } else emptyMap()

    /** Bulk-fetches messages and steps in two queries across all scenario IDs, then groups them for O(1) lookup per scenario. */
    private suspend fun buildScenarioLookup(scenariosFromReports: List<ScenarioReportEntity>): ScenarioReportLookup {
        val allScenarioIds = scenariosFromReports.map { it.id }
        val messagesByScenario = if (allScenarioIds.isNotEmpty()) {
            scenarioReportMessageRepository.findByScenarioReportIdInOrderById(allScenarioIds)
                .groupBy { it.scenarioReportId }
        } else emptyMap()
        val stepsByScenario = if (allScenarioIds.isNotEmpty()) {
            stepReportRepository.findByScenarioReportIdIn(allScenarioIds).groupBy { it.scenarioReportId }
        } else emptyMap()
        return ScenarioReportLookup(
            messagesByScenario,
            stepsByScenario,
            scenariosFromReports.groupBy { it.campaignReportId })
    }

    /**
     * Fetches campaign configurations first (N calls, unavoidable) to collect the union of all zone keys,
     * then resolves all zones in a single call.
     */
    private suspend fun resolveZonesAndConfigs(
        tenant: String,
        completedCampaigns: List<Campaign>
    ): Pair<Map<String, CampaignConfiguration?>, Map<String, Zone>> {
        val configByCampaignKey = completedCampaigns.associate { campaign ->
            campaign.key to runCatching { campaignService.retrieveConfiguration(tenant, campaign.key) }.getOrNull()
        }
        val allZoneKeys = configByCampaignKey.values.filterNotNull()
            .flatMap { cfg -> cfg.scenarios.values.flatMap { it.zones?.keys ?: emptySet() } }
            .toSet()
        return configByCampaignKey to zoneService.resolve(tenant, allZoneKeys).associateBy { it.key }
    }

    /** Skips the enricher call entirely when there are no completed campaigns, avoiding an unnecessary remote call. */
    private suspend fun fetchMeters(
        tenant: String,
        completedCampaigns: List<Campaign>,
        scenarioNames: List<String>
    ): Map<String, MeterDistribution> {
        val completedKeys = completedCampaigns.map { it.key }
        return if (completedKeys.isNotEmpty()) {
            campaignMeterEnricher.distribute(tenant, completedKeys, scenarioNames)
        } else emptyMap()
    }

    /**
     * Assembles a [CampaignExecutionDetails] from pre-fetched bulk data.
     * When a campaign report exists the scenarios come from completed report entities; otherwise placeholder
     * details are built from the scenario entities directly to represent in-progress or queued campaigns.
     */
    private fun buildCampaignReport(
        campaign: Campaign,
        campaignReport: CampaignReportEntity?,
        scenarioLookup: ScenarioReportLookup,
        ongoingScenarios: Collection<CampaignScenarioEntity>,
        config: CampaignConfiguration?,
        resolvedZonesByKey: Map<String, Zone>,
        meterDistribution: MeterDistribution
    ): CampaignExecutionDetails {
        val scheduledMinionsByScenario = campaign.configuredScenarios.associate { it.name to it.minionsCount }
        val scenariosExecutionDetails = if (campaignReport != null) {
            scenarioLookup.scenariosByReportId[campaignReport.id].orEmpty().map { scenarioEntity ->
                buildScenarioExecutionDetails(
                    scenarioEntity = scenarioEntity,
                    scheduledMinions = scheduledMinionsByScenario[scenarioEntity.name],
                    messages = mapScenarioReportMessageEntity(scenarioLookup.messagesByScenario[scenarioEntity.id].orEmpty()),
                    stepEntities = scenarioLookup.stepsByScenario[scenarioEntity.id].orEmpty(),
                    meterDistribution = meterDistribution,
                    zoneDistribution = config?.scenarios?.get(scenarioEntity.name)?.zones ?: emptyMap()
                )
            }
        } else {
            ongoingScenarios.map { scenario ->
                ScenarioExecutionDetails(
                    id = scenario.name, name = scenario.name, start = scenario.start, end = null,
                    startedMinions = null, completedMinions = null,
                    successfulExecutions = null, failedExecutions = null,
                    scheduledMinions = scenario.minionsCount,
                    status = if (scenario.start == null) QUEUED else IN_PROGRESS,
                    messages = emptyList()
                )
            }
        }
        val campaignZoneKeys =
            config?.scenarios?.values?.flatMap { it.zones?.keys ?: emptySet() }?.toSet() ?: emptySet()
        return CampaignExecutionDetails(
            version = campaign.version,
            key = campaign.key,
            creation = campaign.creation,
            name = campaign.name,
            speedFactor = campaign.speedFactor,
            scheduledMinions = campaign.scheduledMinions,
            softTimeout = campaign.softTimeout,
            hardTimeout = campaign.hardTimeout,
            start = campaign.start,
            end = campaign.end,
            status = when {
                campaignReport != null -> campaignReport.status
                ongoingScenarios.isNotEmpty() -> IN_PROGRESS
                else -> campaign.status
            },
            failureReason = campaign.failureReason,
            configurerName = campaign.configurerName,
            aborterName = campaign.aborterName,
            zones = campaign.zones,
            startedMinions = campaignReport?.startedMinions,
            completedMinions = campaignReport?.completedMinions,
            successfulExecutions = campaignReport?.successfulExecutions?.toLong(),
            failedExecutions = campaignReport?.failedExecutions?.toLong(),
            scenarios = scenariosExecutionDetails,
            meters = meterDistribution.campaignMeters
        ).also { it.resolvedZones = campaignZoneKeys.mapNotNull { key -> resolvedZonesByKey[key] } }
    }

    private fun buildScenarioExecutionDetails(
        scenarioEntity: ScenarioReportEntity,
        scheduledMinions: Int?,
        messages: List<ReportMessage>,
        stepEntities: List<StepReportEntity>,
        meterDistribution: MeterDistribution,
        zoneDistribution: Map<String, Int>
    ): ScenarioExecutionDetails {
        val steps = stepEntities.map { step ->
            val stepMessages = messages.filter { it.stepName == step.name }
            val stepStatus = when {
                !step.initialized -> ExecutionStatus.FAILED
                step.initializationError != null -> ExecutionStatus.FAILED
                stepMessages.any { it.severity == ReportMessageSeverity.ERROR } -> ExecutionStatus.FAILED
                stepMessages.any { it.severity == ReportMessageSeverity.WARN } -> ExecutionStatus.WARNING
                step.failedExecutions > 0 -> ExecutionStatus.WARNING
                else -> ExecutionStatus.SUCCESSFUL
            }
            StepExecutionDetails(
                name = step.name,
                successfulExecutions = step.successfulExecutions,
                failedExecutions = step.failedExecutions,
                status = stepStatus,
                messages = stepMessages,
                meters = meterDistribution.stepMeters(scenarioEntity.name, step.name)
            )
        }
        return ScenarioExecutionDetails(
            id = scenarioEntity.name,
            name = scenarioEntity.name,
            start = scenarioEntity.start,
            end = scenarioEntity.end,
            scheduledMinions = scheduledMinions,
            startedMinions = scenarioEntity.startedMinions,
            completedMinions = scenarioEntity.completedMinions,
            successfulExecutions = scenarioEntity.successfulExecutions.toLong(),
            failedExecutions = scenarioEntity.failedExecutions.toLong(),
            status = scenarioEntity.status,
            messages = messages.filter { msg -> steps.none { it.name == msg.stepName } },
            steps = steps,
            meters = meterDistribution.scenarioMeters(scenarioEntity.name),
            zoneDistribution = zoneDistribution
        )
    }

    private fun mapScenarioReportMessageEntity(scenarioReportMessageEntities: List<ScenarioReportMessageEntity>): List<ReportMessage> {
        return scenarioReportMessageEntities.map {
            ReportMessage(
                stepName = it.stepName,
                messageId = it.messageId,
                severity = it.severity,
                message = it.message
            )
        }
    }

    /** Holds the three bulk-fetched lookup maps needed to assemble scenario and step details without extra per-scenario queries. */
    private data class ScenarioReportLookup(
        val messagesByScenario: Map<Long, List<ScenarioReportMessageEntity>>,
        val stepsByScenario: Map<Long, List<StepReportEntity>>,
        val scenariosByReportId: Map<Long, List<ScenarioReportEntity>>
    )

    private companion object {

        /**
         * Considered campaign statuses when the campaign is ongoing.
         */
        val RUNNING_STATUSES = setOf(IN_PROGRESS, QUEUED, SCHEDULED)
    }
}
