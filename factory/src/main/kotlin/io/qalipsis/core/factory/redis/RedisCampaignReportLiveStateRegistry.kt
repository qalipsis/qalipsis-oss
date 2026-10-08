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

import io.aerisconsulting.catadioptre.KTestable
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisHashAsyncCommands
import io.lettuce.core.api.async.RedisListAsyncCommands
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.validation.constraints.Positive

@Singleton
@Requirements(
    Requires(beans = [StatefulRedisConnection::class]),
    Requires(env = [ExecutionEnvironments.FACTORY])
)
@ExperimentalLettuceCoroutinesApi
class RedisCampaignReportLiveStateRegistry(
    private val redisHashCommands: RedisHashAsyncCommands<String, String>,
    private val redisListCommands: RedisListAsyncCommands<String, String>,
    private val idGenerator: IdGenerator,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundScope: CoroutineScope,
    @Positive @Property(name = "report.live-state.batch-size", defaultValue = "200")
    private val batchSize: Int = 200,
    @PositiveDuration @Property(name = "report.live-state.linger-duration", defaultValue = "1s")
    private val lingerDuration: Duration = Duration.ofSeconds(1)
) : CampaignReportLiveStateRegistry, CampaignLifeCycleAware {

    /**
     * Counters to add to the shared registry, by key then field. They are aggregated locally and only pushed
     * periodically, to keep the volume of commands sent to the registry independent from the executed load.
     */
    private val pendingCounters = ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>>()

    /**
     * Count of updates received since the latest flush.
     */
    private val pendingUpdates = AtomicInteger()

    /**
     * Job periodically flushing the aggregated counters into the shared registry.
     */
    private var flushJob: Job? = null

    override suspend fun init(campaign: Campaign) {
        flushJob = backgroundScope.launch {
            while (isActive) {
                delay(lingerDuration.toMillis())
                flush()
            }
        }
    }

    override suspend fun close(campaign: Campaign) {
        flushJob?.cancelAndJoin()
        flushJob = null
        flush()
        pendingCounters.clear()
    }

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
        val key = buildRedisReportKey(campaignKey, scenarioName)
        aggregate(key, STARTED_MINIONS_FIELD, count.toLong())
        aggregate(key, RUNNING_MINIONS_FIELD, count.toLong())
    }

    @LogInput
    override suspend fun recordCompletedMinion(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        count: Int
    ) {
        val key = buildRedisReportKey(campaignKey, scenarioName)
        aggregate(key, COMPLETED_MINIONS_FIELD, count.toLong())
        aggregate(key, RUNNING_MINIONS_FIELD, -1 * count.toLong())
    }

    @LogInput
    override suspend fun recordFailedStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        dagId: DirectedAcyclicGraphName,
        underLoad: Boolean,
        cause: Throwable?
    ) {
        val key = buildRedisReportKey(campaignKey, scenarioName) + FAILED_STEP_INITIALIZATION_KEY_POSTFIX
        val causeName = cause?.javaClass?.canonicalName?.let { "$it: " } ?: ""
        val causeMessage = cause?.message ?: "<Unknown>"
        redisHashCommands.hset(key, "$dagId:$underLoad:$stepName", "$causeName$causeMessage")
    }

    @LogInput
    override suspend fun recordSuccessfulStepInitialization(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        dagId: DirectedAcyclicGraphName,
        underLoad: Boolean
    ) {
        val key = buildRedisReportKey(campaignKey, scenarioName) + SUCCESSFUL_STEP_INITIALIZATION_KEY_POSTFIX
        redisListCommands.rpush(key, "$dagId:$underLoad:$stepName")
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
        aggregate(failureKey, "$stepName:$causeName", count.toLong())
    }

    @LogInput
    override suspend fun recordSuccessfulStepExecution(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        count: Int
    ) {
        val key = buildRedisReportKey(campaignKey, scenarioName) + SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX
        aggregate(key, stepName, count.toLong())
    }

    /**
     * Aggregates the value to add to the counter, and flushes all of them when enough updates were received.
     */
    private fun aggregate(key: String, field: String, count: Long) {
        pendingCounters.computeIfAbsent(key) { ConcurrentHashMap() }
            .computeIfAbsent(field) { AtomicLong() }
            .addAndGet(count)
        if (pendingUpdates.incrementAndGet() >= batchSize) {
            backgroundScope.launch { flush() }
        }
    }

    /**
     * Pushes all the aggregated counters into the shared registry, at most one command by counter, and waits for
     * their acknowledgement: the campaign report is generated as soon as the factories confirm their shutdown,
     * hence the counters have to be effectively stored by then.
     */
    @KTestable
    private suspend fun flush() {
        pendingUpdates.set(0)
        val acknowledgements = mutableListOf<RedisFuture<Long>>()
        pendingCounters.forEach { (key, fields) ->
            fields.forEach { (field, counter) ->
                val value = counter.getAndSet(0)
                if (value != 0L) {
                    acknowledgements += redisHashCommands.hincrby(key, field, value)
                }
            }
        }
        // The commands were pipelined, hence all the acknowledgements are received in a unique round trip.
        acknowledgements.forEach { acknowledgement ->
            tryAndLogOrNull(log) { acknowledgement.await() }
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()

        const val STARTED_MINIONS_FIELD = "__started-minions"

        const val COMPLETED_MINIONS_FIELD = "__completed-minions"

        const val RUNNING_MINIONS_FIELD = "__running-minions"

        const val SUCCESSFUL_STEP_EXECUTION_KEY_POSTFIX = ":successful-step-executions"

        const val FAILED_STEP_EXECUTION_KEY_POSTFIX = ":failed-step-executions"

        const val SUCCESSFUL_STEP_INITIALIZATION_KEY_POSTFIX = ":successful-step-initializations"

        const val FAILED_STEP_INITIALIZATION_KEY_POSTFIX = ":failed-step-initializations"

    }

}