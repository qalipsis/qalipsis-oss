package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.redis.RedisUtils
import jakarta.inject.Singleton

@Singleton
@Requires(notEnv = [ExecutionEnvironments.STANDALONE])
@ExperimentalLettuceCoroutinesApi
internal class RedisCampaignReportLiveStateRegistry(
    factoryConfiguration: FactoryConfiguration,
    private val redisCommands: RedisCoroutinesCommands<String, String>,
    private val idGenerator: IdGenerator
) : CampaignReportLiveStateRegistry {

    val keysPrefix = RedisUtils.buildKeysPrefixForTenant(factoryConfiguration.tenant)

    @LogInput
    override suspend fun delete(campaignId: CampaignId, scenarioId: ScenarioId, stepId: StepId, messageId: Any) {
        val field = "${stepId}/${messageId}"
        redisCommands.hdel(buildRedisReportKey(campaignId, scenarioId), field)
    }

    @LogInputAndOutput
    override suspend fun put(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        severity: ReportMessageSeverity,
        messageId: Any?,
        message: String
    ): Any {
        return (messageId?.toString()?.takeIf(String::isNotBlank) ?: idGenerator.short()).also { id ->
            val field = "${stepId}/${id}"
            val value = "${severity}/${message.trim()}"
            redisCommands.hset(buildRedisReportKey(campaignId, scenarioId), field, value)
        }
    }

    /**
     * Creates the common prefix of all the keys used in the assignment process.
     *
     * A Hash tag is used in the key prefix to locate all the values related to the same campaign.
     */
    private fun buildRedisReportKey(
        campaignId: CampaignId, scenarioId: ScenarioId
    ) = "${keysPrefix}${campaignId}-report:${scenarioId}"


    @LogInput
    override suspend fun recordCompletedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int): Long {
        redisCommands.hincrby(buildRedisReportKey(campaignId, scenarioId), COMPLETED_MINIONS_FIELD, count.toLong())
        return redisCommands.hincrby(
            buildRedisReportKey(campaignId, scenarioId),
            RUNNING_MINIONS_FIELD,
            -1 * count.toLong()
        ) ?: 0
    }

    @LogInput
    override suspend fun recordFailedStepExecution(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        count: Int
    ): Long {
        val key = buildRedisReportKey(campaignId, scenarioId) + FAILED_STEP_EXECUTION_KEY_POSTFIX
        return redisCommands.hincrby(key, stepId, count.toLong()) ?: 0
    }

    @LogInput
    override suspend fun recordStartedMinion(campaignId: CampaignId, scenarioId: ScenarioId, count: Int): Long {
        redisCommands.hincrby(buildRedisReportKey(campaignId, scenarioId), STARTED_MINIONS_FIELD, count.toLong())
        return redisCommands.hincrby(buildRedisReportKey(campaignId, scenarioId), RUNNING_MINIONS_FIELD, count.toLong())
            ?: 0
    }

    @LogInput
    override suspend fun recordSuccessfulStepExecution(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        stepId: StepId,
        count: Int
    ): Long {
        val key = buildRedisReportKey(campaignId, scenarioId) + SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX
        return redisCommands.hincrby(key, stepId, count.toLong()) ?: 0
    }

    private companion object {

        const val STARTED_MINIONS_FIELD = "__started-minions"

        const val COMPLETED_MINIONS_FIELD = "__completed-minions"

        const val RUNNING_MINIONS_FIELD = "__running-minions"

        const val SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX = ":successful-step-executions"

        const val FAILED_STEP_EXECUTION_KEY_POSTFIX = ":failed-step-executions"

    }

}