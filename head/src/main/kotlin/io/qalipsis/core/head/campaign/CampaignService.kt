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

package io.qalipsis.core.head.campaign

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import java.time.Instant

/**
 * Service in charge of maintaining the campaigns.
 *
 * @author Eric Jessé
 */
internal interface CampaignService {

    /**
     * Saves a new campaign for the first time.
     * @param configurer consider the user's name  who configure the campaign
     */
    suspend fun create(
        tenant: String,
        configurer: String,
        campaignConfiguration: CampaignConfiguration
    ): RunningCampaign

    suspend fun retrieve(tenant: String, campaignKey: CampaignKey): Campaign

    /**
     * Marks the campaign as in preparation.
     */
    suspend fun prepare(tenant: String, campaignKey: CampaignKey)

    /**
     * Marks the campaign as started.
     */
    suspend fun start(tenant: String, campaignKey: CampaignKey, start: Instant, timeout: Instant?)

    /**
     * Marks the scenario as started.
     */
    suspend fun startScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName, start: Instant)

    /**
     * Marks the scenario as completed.
     */
    suspend fun closeScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Marks a campaign as completed.
     */
    suspend fun close(tenant: String, campaignKey: CampaignKey, result: ExecutionStatus): Campaign

    /**
     * Returns list of all campaigns. Filter is a comma-separated list of values to apply
     * as wildcard filters on the campaign, user and scenario names. For example,  “foo,bar” and *foo* or *bar* will
     * be searched in both the campaign, user and the scenario names
     */
    suspend fun search(tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int): Page<Campaign>

    /**
     * Sets the user who aborted the campaign.
     */
    suspend fun abort(tenant: String, aborter: String, campaignKey: CampaignKey)
}