package io.qalipsis.core.head.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.report.DefaultScenarioReportingExecutionState
import io.qalipsis.core.head.report.toCampaignReport
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import java.time.Instant

/**
 * Implementation of [CampaignReportStateKeeper] based upon data storage in Redis.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.HEAD])
@ExperimentalLettuceCoroutinesApi
internal class RedisCampaignReportStateKeeper(
    private val redisKeyCommands: RedisKeyCoroutinesCommands<String, String>,
    private val redisSetCommands: RedisSetCoroutinesCommands<String, String>,
    private val redisHashCommands: RedisHashCoroutinesCommands<String, String>
) : CampaignReportStateKeeper {

    override suspend fun clear(campaignKey: CampaignKey) {
        redisKeyCommands.keys("$campaignKey-report:*").onEach {
            // Since not all the keys are on the same node of a Redis cluster (no Hash tag is used in the keys),
            // we have to delete them one by one.
            // This is done on purpose to distribute the load implied by all the concurrent scenarios.
            redisKeyCommands.unlink(it)
        }.count()
    }

    override suspend fun start(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        val key = "$campaignKey-report:$RUNNING_SCENARIOS_KEY_POSTFIX"
        redisSetCommands.sadd(key, scenarioName)
        redisHashCommands.hset(
            buildRedisReportKey(campaignKey, scenarioName),
            START_TIMESTAMP_FIELD,
            Instant.now().toString()
        )
    }

    override suspend fun complete(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        redisHashCommands.hset(
            buildRedisReportKey(campaignKey, scenarioName),
            END_TIMESTAMP_FIELD,
            Instant.now().toString()
        )
    }

    override suspend fun complete(campaignKey: CampaignKey) = Unit

    override suspend fun abort(campaignKey: CampaignKey) {
        val scenarios =
            redisSetCommands.smembers("$campaignKey-report:$RUNNING_SCENARIOS_KEY_POSTFIX").toSet(mutableSetOf())
        val abortTimestamp = Instant.now().toString()
        scenarios.forEach { scenarioName ->
            redisHashCommands.hset(
                buildRedisReportKey(campaignKey, scenarioName),
                ABORTED_TIMESTAMP_FIELD,
                abortTimestamp
            )
        }
    }

    override suspend fun generateReport(campaignKey: CampaignKey): CampaignReport {
        val scenarios =
            redisSetCommands.smembers("$campaignKey-report:$RUNNING_SCENARIOS_KEY_POSTFIX").toSet(mutableSetOf())

        return scenarios.map { scenarioName ->
            val rootKey = buildRedisReportKey(campaignKey, scenarioName)
            val scenarioData =
                redisHashCommands.hgetall(rootKey).toList(mutableListOf()).associate { kv -> kv.key to kv.value }
                    .toMutableMap()
            val successes =
                redisHashCommands.hgetall(rootKey + SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX).toList(mutableListOf())
                    .associate { kv -> kv.key to (kv.value?.toInt() ?: 0) }
            val failures =
                redisHashCommands.hgetall(rootKey + FAILED_STEP_EXECUTION_KEY_POSTFIX).toList(mutableListOf())
                    .associate { kv -> kv.key to (kv.value?.toInt() ?: 0) }

            val startTimestamp = Instant.parse(scenarioData.remove(START_TIMESTAMP_FIELD))
            val endTimestamp = scenarioData.remove(END_TIMESTAMP_FIELD)?.let(Instant::parse)
            val abortTimestamp = scenarioData.remove(ABORTED_TIMESTAMP_FIELD)?.let(Instant::parse)
            val status = if (abortTimestamp != null) ExecutionStatus.ABORTED else null

            val messages = scenarioData
                // All the messages have a key containing the step and messages IDs, separated by a slash.
                .filterKeys { it.contains("/") }
                .map { (key, value) ->
                    val messageId = key.substringAfterLast("/")
                    val stepName = key.substringBefore("/$messageId")
                    val severity = ReportMessageSeverity.valueOf(value.substringBefore("/"))
                    val message = value.substringAfter("/")
                    (messageId as Any) to ReportMessage(stepName, messageId, severity, message)
                }.toMap()

            DefaultScenarioReportingExecutionState(
                scenarioName = scenarioName,
                start = startTimestamp,
                startedMinions = scenarioData[STARTED_MINIONS_FIELD]?.toInt() ?: 0,
                completedMinions = scenarioData[COMPLETED_MINIONS_FIELD]?.toInt() ?: 0,
                successfulStepExecutions = successes.values.sum(),
                failedStepExecutions = failures.values.sum(),
                end = endTimestamp,
                abort = abortTimestamp,
                status = status,
                messages = messages
            ).toReport(campaignKey)
        }.toCampaignReport()
    }

    /**
     * Creates the common prefix of all the keys used in the assignment process.
     *
     * A Hash tag is used in the key prefix to locate all the values related to the same campaign.
     */
    private fun buildRedisReportKey(
        campaignKey: CampaignKey, scenarioName: ScenarioName
    ) = "$campaignKey-report:${scenarioName}"

    private companion object {

        const val STARTED_MINIONS_FIELD = "__started-minions"

        const val COMPLETED_MINIONS_FIELD = "__completed-minions"

        const val RUNNING_SCENARIOS_KEY_POSTFIX = ":scenarios-timestamps"

        const val SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX = ":successful-step-executions"

        const val FAILED_STEP_EXECUTION_KEY_POSTFIX = ":failed-step-executions"

        const val START_TIMESTAMP_FIELD = "__start"

        const val END_TIMESTAMP_FIELD = "__end"

        const val ABORTED_TIMESTAMP_FIELD = "__aborted"

    }
}