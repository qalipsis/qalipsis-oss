/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.head.orchestration

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus

/**
 * Service in charge of keep track of the campaign behaviors.
 *
 * @author Eric Jessé
 */
interface CampaignReportStateKeeper {

    /**
     * Cleans all the states and messages kept into the registry for the provided campaign.
     */
    suspend fun clear(campaignKey: CampaignKey)

    /**
     * Notifies the start of a new campaign.
     */
    suspend fun start(campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Notifies the completion of a scenario in a campaign, whether successful or not.
     */
    suspend fun complete(campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Notifies the completion of the whole campaign, whether successful or not.
     *
     * @param campaignKey the key to identify the campaign
     * @param result the temporary result to set, defaults to [ExecutionStatus.SUCCESSFUL]
     * @param failureReason the potential message to identify the failure, defaults to null
     */
    suspend fun complete(
        campaignKey: CampaignKey,
        result: ExecutionStatus = ExecutionStatus.SUCCESSFUL,
        failureReason: String? = null
    )

    /**
     * Releases the campaign.
     */
    suspend fun abort(campaignKey: CampaignKey)

    /**
     * Reports the state of all the scenarios executed in a campaign.
     */
    suspend fun generateReport(campaignKey: CampaignKey): CampaignReport?
}
