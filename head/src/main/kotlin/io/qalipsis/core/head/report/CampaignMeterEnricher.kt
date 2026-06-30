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

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.report.TimeSeriesDataProvider
import io.qalipsis.api.report.TimeSeriesMeter
import jakarta.annotation.Nullable
import jakarta.inject.Singleton

/**
 * Helper that fetches all campaign meters from an available [TimeSeriesDataProvider] and distributes
 * them by scenario and step so that each level of the execution report receives only the relevant subset.
 *
 * When no [TimeSeriesDataProvider] is present in the context (e.g., transient standalone mode without
 * H2 or PostgreSQL), the result contains only empty lists.
 *
 * @author Eric Jessé
 */
@Singleton
class CampaignMeterEnricher(@Nullable private val timeSeriesDataProvider: TimeSeriesDataProvider?) {

    /**
     * Loads all meters for the given campaigns and partitions them by campaign, then by scenario and step.
     *
     * @param tenant the reference of the tenant owning the data.
     * @param campaignKeys the unique keys of the campaigns; must not be empty.
     * @param scenarioNames names of the scenarios executed during the campaigns.
     * @return a map from campaign key to [MeterDistribution] ready to be applied to each campaign's report.
     */
    suspend fun distribute(
        tenant: String,
        campaignKeys: Collection<CampaignKey>,
        scenarioNames: Collection<String>
    ): Map<CampaignKey, MeterDistribution> {
        if (campaignKeys.isEmpty()) return emptyMap()
        val all = timeSeriesDataProvider
            ?.retrieveCampaignMeters(tenant, campaignKeys, scenarioNames)
            ?: return campaignKeys.associateWith { MeterDistribution(emptyList(), emptyMap(), emptyMap()) }

        val byCampaign = all.groupBy { it.campaign }

        return campaignKeys.associateWith { campaignKey ->
            val campaignMeters = mutableListOf<TimeSeriesMeter>()
            val byScenario = mutableMapOf<String, MutableList<TimeSeriesMeter>>()
            val byScenarioAndStep = mutableMapOf<String, MutableMap<String, MutableList<TimeSeriesMeter>>>()
            byCampaign[campaignKey]?.forEach { meter ->
                val scenario = meter.scenario
                val step = meter.tags?.get(TAG_STEP)
                when {
                    scenario == null -> campaignMeters += meter
                    step == null -> byScenario.getOrPut(scenario) { mutableListOf() } += meter
                    else -> byScenarioAndStep
                        .getOrPut(scenario) { mutableMapOf() }
                        .getOrPut(step) { mutableListOf() } += meter
                }
            }
            MeterDistribution(campaignMeters, byScenario, byScenarioAndStep)
        }
    }

    private companion object {
        const val TAG_STEP = "step"
    }
}

/**
 * Result of partitioning campaign meters across the three levels of the execution report.
 */
data class MeterDistribution(
    val campaignMeters: List<TimeSeriesMeter>,
    val byScenario: Map<String, List<TimeSeriesMeter>>,
    val byScenarioAndStep: Map<String, Map<String, List<TimeSeriesMeter>>>
) {

    fun scenarioMeters(scenarioName: String): List<TimeSeriesMeter> = byScenario[scenarioName].orEmpty()

    fun stepMeters(scenarioName: String, stepName: String): List<TimeSeriesMeter> =
        byScenarioAndStep[scenarioName]?.get(stepName).orEmpty()
}
