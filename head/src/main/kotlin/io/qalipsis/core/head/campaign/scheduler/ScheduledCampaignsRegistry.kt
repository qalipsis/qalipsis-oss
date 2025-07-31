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

package io.qalipsis.core.head.campaign.scheduler

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.lang.tryAndLogOrNull
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages records of scheduled campaign's job.
 *
 * @author Francisca Eze
 */
@Singleton
class ScheduledCampaignsRegistry {

    @KTestable
    private val campaignScheduleKeyStore: ConcurrentHashMap<CampaignKey, Job> = ConcurrentHashMap()

    /**
     * Cancels a scheduled test campaign job.
     *
     * @param campaignKey identifier to the job to be cancelled
     */
    suspend fun cancelSchedule(campaignKey: CampaignKey) {
        tryAndLogOrNull(log) { campaignScheduleKeyStore[campaignKey]?.cancel() }
        campaignScheduleKeyStore.remove(campaignKey)
    }

    /**
     * Updates the campaign schedule keystore.
     *
     * @param campaignKey identifier to the job to be added to the store
     * @param scheduleJob background job that handles scheduling of the test campaign
     */
    suspend fun updateSchedule(campaignKey: CampaignKey, scheduleJob: Job) {
        campaignScheduleKeyStore[campaignKey] = scheduleJob
    }

    companion object {

        @JvmStatic
        val log = KotlinLogging.logger { }
    }
}