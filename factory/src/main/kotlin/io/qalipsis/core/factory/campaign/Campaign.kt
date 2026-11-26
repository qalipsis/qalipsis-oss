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

package io.qalipsis.core.factory.campaign

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.directives.DispatcherChannel
import java.time.Instant

/**
 * Configuration of a scheduled or running campaign.
 *
 * @property factoriesCount total count of factories running the campaign, this one included
 *
 * @author Eric Jessé
 */
data class Campaign(
    val campaignKey: CampaignKey,
    val speedFactor: Double = 1.0,
    val startOffsetMs: Long = 1000,
    val hardTimeout: Instant? = null,
    val softTimeout: Instant? = null,
    val broadcastChannel: DispatcherChannel,
    val feedbackChannel: DispatcherChannel,
    val scenarios: Map<ScenarioName, ScenarioConfiguration> = emptyMap(),
    val assignments: Collection<FactoryScenarioAssignment>,
    val factoriesCount: Int = 1
)
