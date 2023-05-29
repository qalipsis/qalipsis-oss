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

package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY])
@ExperimentalLettuceCoroutinesApi
internal class RedisCampaignReportLiveStateRegistry(
    private val redisCommands: RedisHashCoroutinesCommands<String, String>,
    private val idGenerator: IdGenerator
) : CampaignReportLiveStateRegistry {

    @LogInput
    override suspend fun delete(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        messageId: Any
    ) {
        val field = "${stepName}/${messageId}"
        redisCommands.hdel(buildRedisReportKey(campaignKey, scenarioName), field)
    }

    @LogInputAndOutput
    override suspend fun put(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        messageId: Any?,
        message: String
    ): Any {
        return (messageId?.toString()?.takeIf(String::isNotBlank) ?: idGenerator.short()).also { id ->
            val field = "${stepName}/${id}"
            val value = "${severity}/${message.trim()}"
            redisCommands.hset(buildRedisReportKey(campaignKey, scenarioName), field, value)
        }
    }

    /**
     * Creates the common prefix of all the keys used in the assignment process.
     *
     * A Hash tag is used in the key prefix to locate all the values related to the same campaign.
     */
    private fun buildRedisReportKey(
        campaignKey: CampaignKey, scenarioName: ScenarioName
    ) = "${campaignKey}-report:${scenarioName}"

    @LogInput
    override suspend fun recordCompletedMinion(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        count: Int
    ): Long {
        redisCommands.hincrby(buildRedisReportKey(campaignKey, scenarioName), COMPLETED_MINIONS_FIELD, count.toLong())
        return redisCommands.hincrby(
            buildRedisReportKey(campaignKey, scenarioName),
            RUNNING_MINIONS_FIELD,
            -1 * count.toLong()
        ) ?: 0
    }

    @LogInput
    override suspend fun recordFailedStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int,
        cause: Throwable?
    ): Long {
        val key = buildRedisReportKey(campaignKey, scenarioName) + FAILED_STEP_EXECUTION_KEY_POSTFIX
        return redisCommands.hincrby(key, stepName, count.toLong()) ?: 0
    }

    @LogInput
    override suspend fun recordStartedMinion(campaignKey: CampaignKey, scenarioName: ScenarioName, count: Int): Long {
        redisCommands.hincrby(buildRedisReportKey(campaignKey, scenarioName), STARTED_MINIONS_FIELD, count.toLong())
        return redisCommands.hincrby(
            buildRedisReportKey(campaignKey, scenarioName),
            RUNNING_MINIONS_FIELD,
            count.toLong()
        )
            ?: 0
    }

    @LogInput
    override suspend fun recordSuccessfulStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ): Long {
        val key = buildRedisReportKey(campaignKey, scenarioName) + SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX
        return redisCommands.hincrby(key, stepName, count.toLong()) ?: 0
    }

    private companion object {

        const val STARTED_MINIONS_FIELD = "__started-minions"

        const val COMPLETED_MINIONS_FIELD = "__completed-minions"

        const val RUNNING_MINIONS_FIELD = "__running-minions"

        const val SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX = ":successful-step-executions"

        const val FAILED_STEP_EXECUTION_KEY_POSTFIX = ":failed-step-executions"

    }

}