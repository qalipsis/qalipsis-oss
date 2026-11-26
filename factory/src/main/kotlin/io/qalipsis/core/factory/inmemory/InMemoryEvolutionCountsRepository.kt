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

package io.qalipsis.core.factory.inmemory

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.core.factory.steps.evolution.EvolutionCompletion
import io.qalipsis.core.factory.steps.evolution.EvolutionCountsRepository
import io.qalipsis.core.factory.steps.evolution.validPercentage
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of [EvolutionCountsRepository] combining the counts of a campaign running on a unique factory.
 *
 * @author Eric Jessé
 */
@Singleton
class InMemoryEvolutionCountsRepository : EvolutionCountsRepository {

    private val counts = ConcurrentHashMap<StepKey, Counts>()

    override suspend fun register(campaignKey: CampaignKey, scenarioName: ScenarioName, stepName: StepName) {
        counts.compute(StepKey(campaignKey, scenarioName, stepName)) { _, current ->
            (current ?: Counts()).let { it.copy(instances = it.instances + 1) }
        }
    }

    override suspend fun increment(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        validDelta: Long,
        invalidDelta: Long
    ): Double? = counts.compute(StepKey(campaignKey, scenarioName, stepName)) { _, current ->
        (current ?: Counts()).add(validDelta, invalidDelta)
    }!!.percentage()

    override suspend fun unregister(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        validDelta: Long,
        invalidDelta: Long
    ): EvolutionCompletion {
        lateinit var completion: EvolutionCompletion
        counts.compute(StepKey(campaignKey, scenarioName, stepName)) { _, current ->
            val updated = (current ?: Counts()).add(validDelta, invalidDelta).let { it.copy(instances = it.instances - 1) }
            completion = EvolutionCompletion(updated.percentage(), last = updated.instances <= 0)
            updated.takeIf { it.instances > 0 }
        }
        return completion
    }

    private data class StepKey(val campaignKey: CampaignKey, val scenarioName: ScenarioName, val stepName: StepName)

    private data class Counts(val valid: Long = 0, val invalid: Long = 0, val instances: Int = 0) {

        fun add(validDelta: Long, invalidDelta: Long) = copy(valid = valid + validDelta, invalid = invalid + invalidDelta)

        fun percentage() = validPercentage(valid, invalid)
    }
}
