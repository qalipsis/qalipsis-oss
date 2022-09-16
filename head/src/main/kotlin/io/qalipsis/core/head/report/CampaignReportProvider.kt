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
import io.qalipsis.api.report.CampaignReport

/**
 * Service to retrieve the report of a campaign from a database.
 *
 * @author Francisca Eze
 */
interface CampaignReportProvider {
    /**
     * Retrieves the CampaignReport matching the specified [campaignKey].
     *
     * @param tenant the reference of the tenant owning the data.
     * @param campaignKey the ID of the campaign to retrieve
     */
    suspend fun retrieveCampaignReport(tenant: String, campaignKey: CampaignKey): CampaignReport
}