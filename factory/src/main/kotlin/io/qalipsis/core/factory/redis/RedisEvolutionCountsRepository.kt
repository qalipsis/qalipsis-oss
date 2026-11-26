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

package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisScriptingCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.steps.evolution.EvolutionCompletion
import io.qalipsis.core.factory.steps.evolution.EvolutionCountsRepository
import io.qalipsis.core.factory.steps.evolution.validPercentage
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Implementation of [EvolutionCountsRepository] combining the counts of all the factories of a campaign in Redis,
 * when the campaign runs on several factories.
 *
 * The counts of a step are stored in a hash with the fields `valid`, `invalid` and `instances`, under the key
 * `qevolution:{<tenant>:<campaign>:<scenario>:<step>}`. Each operation is a single call of the script
 * [SCRIPT], hence atomic, and only accesses that key: the steps are spread over the nodes of a Redis Cluster.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.REDIS]),
    Requires(env = [ExecutionEnvironments.FACTORY])
)
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisEvolutionCountsRepository(
    scriptingCommands: RedisScriptingCoroutinesCommands<String, String>,
    private val factoryConfiguration: FactoryConfiguration,
) : EvolutionCountsRepository {

    private val script = RedisScript(scriptingCommands, SCRIPT)

    private val ttl = TTL.seconds.toString()

    override suspend fun register(campaignKey: CampaignKey, scenarioName: ScenarioName, stepName: StepName) {
        script.execute(listOf(key(campaignKey, scenarioName, stepName)), REGISTER, ttl)
    }

    override suspend fun increment(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        validDelta: Long,
        invalidDelta: Long
    ): Double? {
        val (valid, invalid) = execute(INCREMENT, campaignKey, scenarioName, stepName, validDelta, invalidDelta)
        return validPercentage(valid, invalid)
    }

    override suspend fun unregister(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        validDelta: Long,
        invalidDelta: Long
    ): EvolutionCompletion {
        val (valid, invalid, instances) =
            execute(UNREGISTER, campaignKey, scenarioName, stepName, validDelta, invalidDelta)
        return EvolutionCompletion(validPercentage(valid, invalid), last = instances <= 0)
    }

    /**
     * Executes the [operation] adding the deltas, and returns the integers returned by the script.
     */
    private suspend fun execute(
        operation: String,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        validDelta: Long,
        invalidDelta: Long
    ): List<Long> = script.execute(
        listOf(key(campaignKey, scenarioName, stepName)),
        operation,
        ttl,
        validDelta.toString(),
        invalidDelta.toString()
    ).map { it as Long }

    private fun key(campaignKey: CampaignKey, scenarioName: ScenarioName, stepName: StepName) =
        "qevolution:{${factoryConfiguration.tenant}:${campaignKey}:${scenarioName}:${stepName}}"

    private companion object {

        /**
         * Resource of the script operating on the counts.
         */
        const val SCRIPT = "/redis/evolution-counts.lua"

        const val REGISTER = "register"

        const val INCREMENT = "increment"

        const val UNREGISTER = "unregister"

        /**
         * Expiration of the counts, refreshed by each operation, to remove them when the steps never unregister,
         * for example when all the factories crash.
         */
        val TTL: Duration = Duration.ofHours(24)
    }
}
