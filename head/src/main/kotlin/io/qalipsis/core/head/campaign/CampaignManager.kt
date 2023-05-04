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

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.model.CampaignConfiguration


/**
 * Component to manage the execution of campaigns.
 *
 * @author Eric Jessé
 */
internal interface CampaignManager {

    /**
     * Starts a new campaign with the provided configuration.
     *
     * @param configurer username of the user who configured the campaign
     * @param configuration configuration of the campaign to execute
     */
    suspend fun start(tenant: String, configurer: String, configuration: CampaignConfiguration): RunningCampaign

    /**
     * Aborts a campaign with the provided name.
     * @param aborter username of the user aborting the campaign
     * @param tenant define in which tenant the abortion of the campaign should be done
     * @param campaignKey name of the campaign to abort
     * @param hard force the campaign to fail when set to true, defaults to false
     */
    suspend fun abort(tenant: String, aborter: String, campaignKey: String, hard: Boolean)

    /**
     * Replay a campaign with the provided key.
     * @param configurer username of the user replaying the campaign
     * @param tenant tenant owning the campaign to replay
     * @param campaignKey key of a previous campaign to replay
     */
    suspend fun replay(tenant: String, configurer: String, campaignKey: String): RunningCampaign

    /**
     * Schedule a campaign test with the provided configuration.
     *
     * @param tenant tenant owning the campaign to schedule
     * @param configurer username of the user who configured the campaign
     * @param configuration configuration of the campaign to schedule
     */
    suspend fun schedule(tenant: String, configurer: String, configuration: CampaignConfiguration): RunningCampaign
}
