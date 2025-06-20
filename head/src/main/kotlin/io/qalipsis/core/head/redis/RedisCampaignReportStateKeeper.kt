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

package io.qalipsis.core.head.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisListCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.report.DefaultScenarioReportingExecutionState
import io.qalipsis.core.head.report.toCampaignReport
import io.qalipsis.core.math.percentOf
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import org.slf4j.event.Level.DEBUG
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
    private val redisListCommands: RedisListCoroutinesCommands<String, String>,
    private val redisHashCommands: RedisHashCoroutinesCommands<String, String>
) : CampaignReportStateKeeper {

    @LogInput(level = DEBUG)
    override suspend fun clear(campaignKey: CampaignKey) {
        redisKeyCommands.keys("$campaignKey-report:*").onEach {
            // Since not all the keys are on the same node of a Redis cluster (no Hash tag is used in the keys),
            // we have to delete them one by one.
            // This is done on purpose to distribute the load implied by all the concurrent scenarios.
            redisKeyCommands.unlink(it)
        }.count()
    }

    @LogInputAndOutput(level = DEBUG)
    override suspend fun start(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        val key = "$campaignKey-report:$RUNNING_SCENARIOS_KEY_POSTFIX"
        redisSetCommands.sadd(key, scenarioName)
        redisHashCommands.hset(
            buildRedisReportKey(campaignKey, scenarioName),
            START_TIMESTAMP_FIELD,
            Instant.now().toString()
        )
    }

    @LogInputAndOutput(level = DEBUG)
    override suspend fun complete(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        redisHashCommands.hset(
            buildRedisReportKey(campaignKey, scenarioName),
            END_TIMESTAMP_FIELD,
            Instant.now().toString()
        )
    }

    @LogInputAndOutput(level = DEBUG)
    override suspend fun complete(campaignKey: CampaignKey, result: ExecutionStatus, failureReason: String?) {
        // Saved the provided status and the potential error message.
        val key = "$campaignKey-report:$CAMPAIGN_FORCED_STATUS"
        val status = mutableMapOf(
            CAMPAIGN_FORCED_STATUS_RESULT to "$result"
        )
        if (failureReason != null) {
            status[CAMPAIGN_FORCED_STATUS_FAILURE] = failureReason
        }
        redisHashCommands.hset(key, status)
    }

    @LogInputAndOutput(level = DEBUG)
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

    @LogInput(level = DEBUG)
    override suspend fun generateReport(campaignKey: CampaignKey): CampaignReport {
        val scenarios =
            redisSetCommands.smembers("$campaignKey-report:$RUNNING_SCENARIOS_KEY_POSTFIX").toSet(mutableSetOf())
        val campaignForcedStatus =
            redisHashCommands.hgetall("$campaignKey-report:$CAMPAIGN_FORCED_STATUS").map { it.key to it.value }.toList()
                .toMap()
        val campaignKnownStatus =
            campaignForcedStatus[CAMPAIGN_FORCED_STATUS_RESULT]?.let { ExecutionStatus.valueOf(it) }
        val campaignFailure = campaignForcedStatus[CAMPAIGN_FORCED_STATUS_FAILURE]

        return scenarios.map { scenarioName ->
            val rootKey = buildRedisReportKey(campaignKey, scenarioName)
            val scenarioData =
                redisHashCommands.hgetall(rootKey).toList().associate { kv -> kv.key to kv.value }.toMutableMap()

            // Count of successful and failed executions, keyed by step names.
            val successfulExecutions =
                redisHashCommands.hgetall(rootKey + SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX).toList()
                    .associate { kv -> kv.key to (kv.value?.toInt() ?: 0) }

            // Count of steps failures, stored with the step and exception class name as key, converted as maps of count by errors and by step.
            val failedExecutions = mutableMapOf<String, Int>()
            val executionFailuresDetails =
                redisHashCommands.hgetall(rootKey + FAILED_STEP_EXECUTION_KEY_POSTFIX).toList()
                    // Group by step name.
                    .groupBy { it.key.substringBeforeLast(":") }
                    .flatMap { (stepName, errors) ->
                        var totalFailures = 0
                        val typedErrors = errors.associate {
                            val errorType = it.key.substringAfterLast(":")
                            val errorsCount = it.value?.toIntOrNull() ?: 0
                            totalFailures += errorsCount
                            errorType to errorsCount
                        }
                        failedExecutions[stepName] = totalFailures

                        var index = -1
                        typedErrors.map { (errorType, count) ->
                            index++
                            ReportMessage(
                                stepName,
                                "${stepName}_failure_$index",
                                ReportMessageSeverity.ERROR,
                                "Count of errors $errorType: $count (${count.percentOf(totalFailures)}% of all failures)"
                            )
                        }
                    }

            val startTimestamp = Instant.parse(scenarioData.remove(START_TIMESTAMP_FIELD))
            val endTimestamp = scenarioData.remove(END_TIMESTAMP_FIELD)?.let(Instant::parse)
            val abortTimestamp = scenarioData.remove(ABORTED_TIMESTAMP_FIELD)?.let(Instant::parse)

            val successfullyInitializedSteps =
                redisListCommands.lrange("$rootKey$SUCCESSFUL_STEP_INITIALIZATION_KEY_POSTFIX", 0, -1)
            val initializationFailures =
                redisHashCommands.hgetall("$rootKey$FAILED_STEP_INITIALIZATION_KEY_POSTFIX").toList().map {
                    val stepName = it.key
                    val message = it.value
                    ReportMessage(stepName, "${stepName}_init", ReportMessageSeverity.ERROR, message)
                }
            val initializationMessages = listOf(
                ReportMessage(
                    stepName = "_init",
                    messageId = "_init",
                    severity = ReportMessageSeverity.INFO,
                    message = "Steps successfully initialized: ${successfullyInitializedSteps.joinToString()}"
                )
            ) + initializationFailures

            val messages = scenarioData
                // All the messages have a key containing the step and messages IDs, separated by a slash.
                .filterKeys { it.contains("/") }
                .map { (key, value) ->
                    val messageId = key.substringAfterLast("/")
                    val stepName = key.substringBefore("/$messageId")
                    val severity = ReportMessageSeverity.valueOf(value.substringBefore("/"))
                    val message = value.substringAfter("/")
                    ReportMessage(stepName, messageId, severity, message)
                }

            val allMessages = listOfNotNull(campaignFailure?.let {
                ReportMessage(
                    stepName = "",
                    messageId = "",
                    severity = ReportMessageSeverity.ERROR,
                    message = it
                )
            }) + initializationMessages + executionFailuresDetails + messages
            val status = when {
                campaignKnownStatus == ExecutionStatus.ABORTED -> ExecutionStatus.ABORTED
                campaignKnownStatus == ExecutionStatus.FAILED -> ExecutionStatus.FAILED
                allMessages.any { it.severity == ReportMessageSeverity.ERROR } -> ExecutionStatus.FAILED
                allMessages.any { it.severity == ReportMessageSeverity.WARN } -> ExecutionStatus.WARNING
                else -> ExecutionStatus.SUCCESSFUL
            }
            DefaultScenarioReportingExecutionState(
                scenarioName = scenarioName,
                start = startTimestamp,
                startedMinions = scenarioData[STARTED_MINIONS_FIELD]?.toInt() ?: 0,
                completedMinions = scenarioData[COMPLETED_MINIONS_FIELD]?.toInt() ?: 0,
                successfulStepExecutions = successfulExecutions.values.sum(),
                failedStepExecutions = failedExecutions.values.sum(),
                end = endTimestamp,
                abort = abortTimestamp,
                status = status,
                messages = allMessages
            ).toReport(campaignKey)
        }.toCampaignReport(campaignKnownStatus)
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

        const val CAMPAIGN_FORCED_STATUS = ":campaign-status"

        const val CAMPAIGN_FORCED_STATUS_RESULT = "status"

        const val CAMPAIGN_FORCED_STATUS_FAILURE = "failure"

        const val RUNNING_SCENARIOS_KEY_POSTFIX = ":scenarios-timestamps"

        const val SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX = ":successful-step-executions"

        const val FAILED_STEP_EXECUTION_KEY_POSTFIX = ":failed-step-executions"

        const val SUCCESSFUL_STEP_INITIALIZATION_KEY_POSTFIX = ":successful-step-initializations"

        const val FAILED_STEP_INITIALIZATION_KEY_POSTFIX = ":failed-step-initializations"

        const val START_TIMESTAMP_FIELD = "__start"

        const val END_TIMESTAMP_FIELD = "__end"

        const val ABORTED_TIMESTAMP_FIELD = "__aborted"

    }
}