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

package io.qalipsis.core.head.hook

import io.micronaut.core.order.Ordered
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.model.CampaignConfiguration

/**
 * Service that handle constraints validation for campaign.
 *
 * @author Joël Valère
 */
interface CampaignHook : Ordered {

    /**
     * Hook to process the campaign details before it is created.
     */
    suspend fun preCreate(campaignConfiguration: CampaignConfiguration, runningCampaign: RunningCampaign) = Unit

    /**
     * Hook to process the campaign details before it is scheduled.
     */
    suspend fun preSchedule(campaignConfiguration: CampaignConfiguration, runningCampaign: RunningCampaign) =
        preCreate(campaignConfiguration, runningCampaign)

    /**
     * Hook to process the campaign details when it starts.
     */
    suspend fun preStart(runningCampaign: RunningCampaign) = Unit

    /**
     * Hook to process the campaign details after it terminates.
     */
    suspend fun afterStop(campaignKey: CampaignKey) = Unit

}