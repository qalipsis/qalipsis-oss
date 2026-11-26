/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.steps.evolution

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName

/**
 * Repository combining the counts of valid and invalid transitions of an evolution verification step, over all its
 * instances (one per factory) in a campaign. Only the aggregated counts of each step are stored, not the values.
 *
 * Each operation is a single atomic call to the storage.
 *
 * @author Eric Jessé
 */
interface EvolutionCountsRepository {

    /**
     * Registers a new instance of the step, which will have to [unregister] when it stops.
     */
    suspend fun register(campaignKey: CampaignKey, scenarioName: ScenarioName, stepName: StepName)

    /**
     * Adds the deltas of transitions of an instance of the step.
     *
     * @return the percentage of valid transitions over all the instances, null when there is no transition yet
     */
    suspend fun increment(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        validDelta: Long,
        invalidDelta: Long
    ): Double?

    /**
     * Adds the last deltas of transitions of an instance of the step and unregisters it. The counts are deleted
     * when the last instance unregisters.
     */
    suspend fun unregister(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        validDelta: Long,
        invalidDelta: Long
    ): EvolutionCompletion
}

/**
 * Result of the unregistration of an instance of an evolution verification step.
 *
 * @property percentile percentage of valid transitions over all the instances, null when there was no transition
 * @property last true when the unregistered instance was the last registered one, and should report the verification
 */
data class EvolutionCompletion(val percentile: Double?, val last: Boolean)

/**
 * Percentage of [valid] transitions in all the transitions, null when there is none.
 */
internal fun validPercentage(valid: Long, invalid: Long): Double? =
    (valid + invalid).takeIf { it > 0 }?.let { total -> valid * 100.0 / total }
