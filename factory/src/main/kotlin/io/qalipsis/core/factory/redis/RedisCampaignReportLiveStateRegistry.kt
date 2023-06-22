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
import io.lettuce.core.api.async.RedisHashAsyncCommands
import io.lettuce.core.api.async.RedisListAsyncCommands
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
    private val redisHashCommands: RedisHashAsyncCommands<String, String>,
    private val redisListCommands: RedisListAsyncCommands<String, String>,
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
        redisHashCommands.hdel(buildRedisReportKey(campaignKey, scenarioName), field)
    }

    @LogInputAndOutput
    override suspend fun put(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        messageId: String?,
        message: String
    ): String {
        return (messageId?.takeIf(String::isNotBlank) ?: idGenerator.short()).also { id ->
            val field = "${stepName}/${id}"
            val value = "${severity}/${message.trim()}"
            redisHashCommands.hset(buildRedisReportKey(campaignKey, scenarioName), field, value)
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
    override suspend fun recordStartedMinion(campaignKey: CampaignKey, scenarioName: ScenarioName, count: Int) {
        redisHashCommands.hincrby(buildRedisReportKey(campaignKey, scenarioName), STARTED_MINIONS_FIELD, count.toLong())
        redisHashCommands.hincrby(buildRedisReportKey(campaignKey, scenarioName), RUNNING_MINIONS_FIELD, count.toLong())
    }

    @LogInput
    override suspend fun recordCompletedMinion(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        count: Int
    ) {
        redisHashCommands.hincrby(
            buildRedisReportKey(campaignKey, scenarioName),
            COMPLETED_MINIONS_FIELD,
            count.toLong()
        )
        redisHashCommands.hincrby(
            buildRedisReportKey(campaignKey, scenarioName),
            RUNNING_MINIONS_FIELD,
            -1 * count.toLong()
        )
    }

    override suspend fun recordFailedStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        cause: Throwable?
    ) {
        val key = buildRedisReportKey(campaignKey, scenarioName) + FAILED_STEP_INITIALIZATION_KEY_POSTFIX
        val causeName = cause?.javaClass?.canonicalName?.let { "$it: " } ?: ""
        val causeMessage = cause?.message ?: "<Unknown>"
        redisHashCommands.hset(key, stepName, "$causeName$causeMessage")
    }

    override suspend fun recordSuccessfulStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName
    ) {
        val key = buildRedisReportKey(campaignKey, scenarioName) + SUCCESSFUL_STEP_INITIALIZATION_KEY_POSTFIX
        redisListCommands.rpush(key, stepName)
    }

    @LogInput
    override suspend fun recordFailedStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int,
        cause: Throwable?
    ) {
        val failureKey = buildRedisReportKey(campaignKey, scenarioName) + FAILED_STEP_EXECUTION_KEY_POSTFIX
        val causeName = cause?.javaClass?.canonicalName ?: "<Unknown>"
        redisHashCommands.hincrby(failureKey, "$stepName:$causeName", count.toLong())
    }

    @LogInput
    override suspend fun recordSuccessfulStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ) {
        val key = buildRedisReportKey(campaignKey, scenarioName) + SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX
        redisHashCommands.hincrby(key, stepName, count.toLong())
    }

    private companion object {

        const val STARTED_MINIONS_FIELD = "__started-minions"

        const val COMPLETED_MINIONS_FIELD = "__completed-minions"

        const val RUNNING_MINIONS_FIELD = "__running-minions"

        const val SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX = ":successful-step-executions"

        const val FAILED_STEP_EXECUTION_KEY_POSTFIX = ":failed-step-executions"

        const val SUCCESSFUL_STEP_INITIALIZATION_KEY_POSTFIX = ":successful-step-initializations"

        const val FAILED_STEP_INITIALIZATION_KEY_POSTFIX = ":failed-step-initializations"

    }

}