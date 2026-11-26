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

package io.qalipsis.core.factory.redis.meters

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.aerisconsulting.catadioptre.KTestable
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisScriptingCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.Executors
import io.qalipsis.api.config.MetersConfig
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.meters.CompositeMeterRegistry
import io.qalipsis.core.factory.meters.MeterRegistry
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.BUCKETS_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.COUNT_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.DENOMINATOR_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.NUMERATOR_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.TOTAL_FIELD
import io.qalipsis.core.factory.redis.meters.RedisMeter.Companion.VALUE_FIELD
import io.qalipsis.core.redis.RedisUtils
import io.qalipsis.core.reporter.MeterReporter
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * [MeterRegistry] combining the meters of all the factories of a campaign in Redis.
 *
 * ## Motivation
 *
 * When a campaign runs on several factories, each factory only records the meters of its own minions. Kept in memory,
 * these meters give each factory a partial view: the expectations of the meter steps are evaluated on a fraction of
 * the values, and each factory publishes its own snapshots, so that the report and the time-series contain one value
 * per factory instead of one for the campaign. This registry combines the meters of all the factories, so that each
 * factory reads the values of the whole campaign and each value is published once.
 *
 * Recording must stay as cheap as in memory: a step can record millions of values per second. Hence no Redis
 * request is made when recording; the local deltas are sent in bulk in the background, and the combined values are
 * read from a local cache refreshed by the flushes.
 *
 * ## Usage
 *
 * The registry is not used directly: [CompositeMeterRegistry] selects it at the start of a campaign running on several
 * factories, calls [init], routes the creation of the meters to it and finally calls [close]. It requires the
 * environments `redis` and `factory` and is disabled with `meters.redis.enabled=false`; see
 * [RedisMetersConfiguration] for the other settings.
 *
 * ## Workflow
 *
 * 1. **Levels**: each meter is combined at up to 3 levels (see [levelsOf]): itself (scenario + zone), its scenario
 *    and the campaign. A level is shared by all the meters of the factory having it in common.
 * 2. **Declaration**: at the next flush, the new levels are declared in the campaign registry in Redis (see
 *    [declareLevels]). Redis assigns a short id to each level, the same for all the factories, which also gives the
 *    shard of the level (`short id % shards`). The first factory declaring a level becomes its owner: only the owner
 *    publishes its snapshots.
 * 3. **Recording**: the meters record into local adders and histograms, without any Redis request.
 * 4. **Flush**: every [linger], the meters are drained into their levels and the deltas are sent with one concurrent
 *    Lua call per shard (see [flushToShard]). Redis adds them to the cumulative values of the shard and returns the
 *    combined values of the sent levels, which refresh the cache read by the meters. Only the primary levels, read by
 *    the meters, are sent every second; the other levels go with the period acknowledgements.
 * 5. **Period barrier**: the first flush after the end of a period of [step] acknowledges the period to all the
 *    shards used by the campaign. When all the factories acknowledged a period to a shard, Redis computes the values
 *    of the period (difference with the cumulative values at the previous barrier), publishes them on the channel of
 *    the shard and keeps a fallback copy.
 * 6. **Publication**: the owners subscribed to the channels of the shards of their levels convert the received
 *    values into period snapshots (see [receive]), returned by the next call to [snapshots].
 * 7. **End of the campaign**: [summarize] stops the periodic flushes and sends a final flush acknowledging the end of
 *    the campaign. When all the factories completed the campaign for a shard, Redis publishes a final message with
 *    the cumulative values, which the owners convert into the campaign snapshots. If it is not received within
 *    [finalTimeout], the cumulative values are read from Redis.
 * 8. **Cleanup**: [close] deletes the keys of the completed shards and of the campaign registry; the other keys
 *    expire after [keysTtl] or [registryTtl].
 *
 * A failed flush keeps its deltas for the next one, and a flush is never cancelled once started, so that a value is
 * neither lost nor counted twice. A failure of Redis never stops the campaign: it is logged and retried at the next
 * flush.
 *
 * @property factoryConfiguration configuration of the factory, for its node id and tenant
 * @property meterReporter reporter displaying the meters in the campaign report
 * @property redisClient client of the standalone Redis or the Redis cluster, to open the Pub/Sub connection
 * @property scriptingCommands commands to execute the Lua scripts
 * @property keyCommands commands to delete the keys at the end of the campaign
 * @property setCommands commands to read the shards used by the campaign
 * @property hashCommands commands to read the cumulative values of a shard when its final message is missing
 * @property backgroundScope scope of the periodic flushes
 * @property step duration of the periods of the meters, which is also the frequency of their snapshots
 * @property configuration configuration of the meters in Redis
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.REDIS]),
    Requires(env = [ExecutionEnvironments.FACTORY]),
    Requires(beans = [AbstractRedisClient::class]),
    Requires(property = "meters.redis.enabled", notEquals = "false")
)
@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class RedisMeterRegistry(
    private val factoryConfiguration: FactoryConfiguration,
    private val meterReporter: MeterReporter,
    private val redisClient: AbstractRedisClient,
    private val scriptingCommands: RedisScriptingCoroutinesCommands<String, String>,
    private val keyCommands: RedisKeyCoroutinesCommands<String, String>,
    private val setCommands: RedisSetCoroutinesCommands<String, String>,
    private val hashCommands: RedisHashCoroutinesCommands<String, String>,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundScope: CoroutineScope,
    @Value("\${${MetersConfig.EXPORT_CONFIGURATION}.campaign-step:PT5S}") private val step: Duration,
    private val configuration: RedisMetersConfiguration,
) : MeterRegistry {

    /**
     * Period between two flushes of the meters to Redis.
     */
    private val linger = configuration.linger

    /**
     * Maximal duration of the final flush, and of the wait for the final values of each owned shard.
     */
    private val finalTimeout = configuration.finalTimeout

    /**
     * Expiration of the keys of the shards, refreshed by each acknowledgement.
     */
    private val keysTtl = configuration.keysTtl

    /**
     * Expiration of the keys of the campaign registry, refreshed by each declaration.
     */
    private val registryTtl = configuration.registryTtl

    /**
     * Count of shards distributing the meter levels over the nodes of a Redis cluster.
     */
    private val shards = configuration.shards

    /**
     * Keys and channels of the current campaign, set by [init].
     */
    private lateinit var keys: RedisMeterKeys

    /**
     * Count of factories running the current campaign: count of acknowledgements completing a period in Redis.
     */
    private var factoriesCount = 1

    /**
     * Identifier of this factory, used as owner of the meter levels and in the acknowledgements.
     */
    private val nodeId: String
        get() = factoryConfiguration.nodeId

    /**
     * Meters of the current campaign, by identifier.
     */
    @KTestable
    private val meters = ConcurrentHashMap<Meter.Id, RedisMeter<*>>()

    /**
     * Levels of all the meters, shared by the meters having a common level.
     */
    @KTestable
    private val levels = ConcurrentHashMap<Meter.Id, RedisMeterLevel>()

    /**
     * Levels created since the latest flush, to declare in Redis at the next one.
     */
    private val undeclaredLevels = ConcurrentLinkedQueue<RedisMeterLevel>()

    /**
     * Declared levels by short id, to match the values returned or published by Redis.
     */
    private val levelsByShortId = ConcurrentHashMap<Long, RedisMeterLevel>()

    /**
     * Shards containing meter levels of the campaign, to acknowledge the periods to.
     */
    @KTestable
    private val usedShards = ConcurrentHashMap.newKeySet<Int>()

    /**
     * Shards containing meter levels owned by this factory, by channel of the shard.
     */
    private val ownedShardsByChannel = ConcurrentHashMap<String, Int>()

    /**
     * Snapshots of the meter levels owned by this factory, received from Redis and not yet returned by [snapshots].
     */
    private val receivedSnapshots = ConcurrentLinkedQueue<MeterSnapshot>()

    /**
     * Latest values of the owned gauges, to publish them when they did not change in a period.
     */
    private val latestGaugeValues = ConcurrentHashMap<Long, Double>()

    /**
     * Final messages of the shards containing meter levels owned by this factory.
     */
    private val finalMessages = ConcurrentHashMap<Int, CompletableDeferred<RedisMeterMessage>>()

    /**
     * Job of the periodic flushes, running from [init] until [summarize].
     */
    private var flushJob: Job? = null

    /**
     * Connection receiving the messages of the owned shards.
     */
    private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null

    /**
     * End of the period of the latest flush, in epoch seconds.
     */
    private var latestPeriod = 0L

    /**
     * True when no campaign is running or its meters were already summarized.
     */
    private var summarized = true

    /**
     * True to use the sharded Pub/Sub (Redis 7.0 or later), false for the plain Pub/Sub.
     */
    private val shardedPubSub = configuration.shardedPubSub

    /**
     * Command used by the flush script to publish the messages, consistent with [shardedPubSub].
     */
    private val publishCommand = if (shardedPubSub) "SPUBLISH" else "PUBLISH"

    /**
     * Script declaring the meter levels in the campaign registry.
     */
    private val declarationScript = Script(DECLARATION_SCRIPT)

    /**
     * Script adding the deltas to a shard, acknowledging the periods and publishing their values.
     */
    private val flushScript = Script(FLUSH_SCRIPT)

    /**
     * Starts combining the meters of [campaign] in Redis: prepares the keys, opens the Pub/Sub connection and starts
     * the periodic flushes.
     */
    override suspend fun init(campaign: Campaign) {
        log.debug { "Combining the meters of the campaign ${campaign.campaignKey} in Redis over ${campaign.factoriesCount} factories" }
        keys = RedisMeterKeys(factoryConfiguration.tenant, campaign.campaignKey, shards)
        factoriesCount = campaign.factoriesCount
        summarized = false
        latestPeriod = periodOf(System.currentTimeMillis())
        subscribe()
        flushJob = backgroundScope.launch {
            while (isActive) {
                delay(linger.toMillis())
                // A flush is never interrupted: Redis might have applied the deltas when the cancellation
                // happens, and sending them again would count them twice.
                withContext(NonCancellable) { flush(final = false) }
            }
        }
    }

    /**
     * Returns the counter identified by [meterId], created with its levels when missing.
     */
    override fun counter(meterId: Meter.Id): Counter = meter(meterId) { RedisCounter(meterId, levelsOf(meterId), meterReporter) }

    /**
     * Returns the gauge identified by [meterId], created with its levels when missing.
     */
    override fun gauge(meterId: Meter.Id): Gauge = meter(meterId) { RedisGauge(meterId, levelsOf(meterId), meterReporter) }

    /**
     * Returns the rate identified by [meterId], created with its levels when missing.
     */
    override fun rate(meterId: Meter.Id): Rate = meter(meterId) { RedisRate(meterId, levelsOf(meterId), meterReporter) }

    /**
     * Returns the timer identified by [meterId], created with its levels and [percentiles] when missing.
     */
    override fun timer(meterId: Meter.Id, percentiles: Set<Double>): Timer =
        meter(meterId) { RedisTimer(meterId, levelsOf(meterId, percentiles), meterReporter, percentiles) }

    /**
     * Returns the distribution summary identified by [meterId], created with its levels and [percentiles] when missing.
     */
    override fun summary(meterId: Meter.Id, percentiles: Set<Double>): DistributionSummary =
        meter(meterId) { RedisDistributionSummary(meterId, levelsOf(meterId, percentiles), meterReporter, percentiles) }

    /**
     * Returns the throughput identified by [meterId], counting its hits by interval of [unit], created with its levels
     * and [percentiles] when missing.
     */
    override fun throughput(meterId: Meter.Id, unit: ChronoUnit, percentiles: Set<Double>): Throughput = meter(meterId) {
        RedisThroughput(meterId, levelsOf(meterId, percentiles, Duration.of(1, unit).toMillis()), meterReporter, unit, percentiles)
    }

    /**
     * Returns the meter identified by [meterId], created by [creator] only once, even with concurrent calls.
     */
    @Suppress("UNCHECKED_CAST")
    private inline fun <T : RedisMeter<*>> meter(meterId: Meter.Id, crossinline creator: () -> T): T =
        meters.computeIfAbsent(meterId) { creator() } as T

    /**
     * Returns the levels of the meter identified by [meterId], itself first, creating the missing ones and queuing
     * them for declaration. The first level is marked as primary: the meter reads its combined values.
     *
     * For a meter tagged `scenario=s1, zone=eu, step=login`, the levels are `scenario=s1, zone=eu, step=login`,
     * `scenario=s1, step=login` and `step=login`; without zone tag, the meter and its campaign level; without scenario
     * tag, only the meter itself.
     *
     * @param percentiles percentiles to publish, for the distributions
     * @param intervalMillis duration of the intervals of a throughput, 0 for the other meters
     */
    private fun levelsOf(
        meterId: Meter.Id,
        percentiles: Collection<Double> = emptyList(),
        intervalMillis: Long = 0,
    ): List<RedisMeterLevel> {
        val tags = meterId.tags
        val levelIds = when {
            SCENARIO_TAG !in tags -> listOf(meterId)
            ZONE_TAG in tags -> listOf(
                meterId,
                meterId.copy(tags = tags - ZONE_TAG),
                meterId.copy(tags = tags - SCENARIO_AND_ZONE_TAGS)
            )

            else -> listOf(meterId, meterId.copy(tags = tags - SCENARIO_TAG))
        }
        return levelIds.map { levelId ->
            levels.computeIfAbsent(levelId) { RedisMeterLevel(it, percentiles, intervalMillis).also(undeclaredLevels::add) }
        }.also { it.first().isPrimary = true }
    }

    /**
     * Returns the period snapshots of the owned meter levels received from Redis since the previous call. [instant] is
     * ignored: the snapshots carry the end of their period, computed by Redis.
     */
    override suspend fun snapshots(instant: Instant): Collection<MeterSnapshot> = drainReceivedSnapshots()

    /**
     * Completes the campaign: stops the periodic flushes, flushes the latest deltas acknowledging the end of the
     * campaign, and returns the snapshots not yet returned plus the campaign snapshots of the owned meter levels, from
     * the final message of each shard. Only the first call after [init] does something, the next ones return nothing.
     */
    override suspend fun summarize(instant: Instant): Collection<MeterSnapshot> {
        if (summarized) {
            return emptyList()
        }
        summarized = true
        flushJob?.cancelAndJoin()
        // A lost connection to Redis should not block the end of the campaign.
        withTimeoutOrNull(finalTimeout.toMillis()) { flush(final = true) }
            ?: log.warn { "The final meters could not be flushed to Redis within $finalTimeout" }
        val ownedLevels = ownedLevels()
        if (ownedLevels.isEmpty()) {
            return drainReceivedSnapshots()
        }
        val cumulativeValues = coroutineScope {
            ownedLevels.map { it.shard }.distinct().map { shard -> async { cumulativeValues(shard) } }.awaitAll()
        }.reduce { all, values -> all + values }
        return drainReceivedSnapshots() + ownedLevels.mapNotNull { level ->
            snapshot(level, cumulativeValues[level.shortId], instant, CAMPAIGN_SCOPE)
        }
    }

    /**
     * Cumulative values of the meter levels of [shard], from its final message once all the factories completed
     * the campaign, or read from Redis when it is not received in time.
     */
    private suspend fun cumulativeValues(shard: Int) = RedisMeterMeasurements.byLevel(
        withTimeoutOrNull(finalTimeout.toMillis()) { finalMessages[shard]?.await() }?.cumulative
            ?: withTimeoutOrNull(finalTimeout.toMillis()) { readCumulativeValues(shard) }.orEmpty()
    )

    /**
     * Completes the campaign if not yet done, deletes its meters from Redis and releases the resources.
     */
    override suspend fun close(campaign: Campaign) {
        summarize(Instant.now().truncatedTo(ChronoUnit.SECONDS))
        tryAndLog("delete the meters from Redis") {
            // The keys of the completed shards are no longer needed: all the factories flushed their values.
            val completedShards = finalMessages.filterValues { it.isCompleted }.keys
            withTimeoutOrNull(finalTimeout.toMillis()) {
                completedShards.forEach { shard ->
                    keyCommands.unlink(keys.cumulative(shard), keys.previous(shard), keys.completions(shard))
                }
                keyCommands.unlink(*keys.registryKeys.toTypedArray())
            }
        }
        tryAndLog("close the Pub/Sub connection") { pubSubConnection?.closeAsync()?.await() }
        pubSubConnection = null
        flushJob = null
        meters.clear()
        levels.clear()
        undeclaredLevels.clear()
        levelsByShortId.clear()
        usedShards.clear()
        ownedShardsByChannel.clear()
        finalMessages.clear()
        receivedSnapshots.clear()
        latestGaugeValues.clear()
    }

    /**
     * Opens the Pub/Sub connection and registers the listener of the messages; the channels are subscribed later by
     * [subscribeToOwnedShards], once the owned levels are known.
     */
    private suspend fun subscribe() {
        val connection = withContext(Dispatchers.IO) {
            when (redisClient) {
                is RedisClusterClient -> redisClient.connectPubSub().also {
                    // The sharded messages are received by the connection to the node owning the slot.
                    it.setNodeMessagePropagation(true)
                }

                is RedisClient -> redisClient.connectPubSub()
                else -> throw IllegalArgumentException("Redis client of type ${redisClient::class} is not supported")
            }
        }
        connection.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) = receive(channel, message)

            override fun smessage(shardChannel: String, message: String) = receive(shardChannel, message)
        })
        pubSubConnection = connection
    }

    /**
     * Subscribes to the messages of the shards containing the owned meter levels, when not yet done.
     * Each channel is subscribed separately, since the channels of the shards have different slots in a cluster.
     */
    private suspend fun subscribeToOwnedShards() {
        val connection = pubSubConnection!!.async()
        ownedLevels().map { it.shard }.distinct().filterNot(finalMessages::containsKey).forEach { shard ->
            val channel = keys.channel(shard)
            if (shardedPubSub) connection.ssubscribe(channel).await() else connection.subscribe(channel).await()
            ownedShardsByChannel[channel] = shard
            finalMessages[shard] = CompletableDeferred()
        }
    }

    /**
     * Converts the values of a period published by Redis on [channel] into snapshots of the meter levels owned by
     * this factory in the shard, and completes the final message of the shard when [payload] is final. The messages
     * of other channels are ignored; a malformed message is logged without interrupting the reception.
     */
    @KTestable
    private fun receive(channel: String, payload: String) {
        try {
            val shard = ownedShardsByChannel[channel] ?: return
            val message = objectMapper.readValue<RedisMeterMessage>(payload)
            val timestamp = Instant.ofEpochSecond(message.period)
            val values = RedisMeterMeasurements.byLevel(message.values)
            ownedLevels().filter { it.shard == shard }.mapNotNullTo(receivedSnapshots) { level ->
                snapshot(level, values[level.shortId], timestamp, PERIOD_SCOPE)
            }
            if (message.final) {
                finalMessages[shard]?.complete(message)
            }
        } catch (e: Exception) {
            log.error(e) { "The meters message could not be processed: ${e.message}" }
        }
    }

    /**
     * Removes and returns all the received snapshots.
     */
    private fun drainReceivedSnapshots(): List<MeterSnapshot> = generateSequence { receivedSnapshots.poll() }.toList()

    /**
     * Declared meter levels owned by this factory.
     */
    private fun ownedLevels() = levels.values.filter { it.isDeclared && it.owner == nodeId }

    /**
     * Creates the snapshot of [level] at [timestamp] with the [scope] tag (period or campaign), from its [normalized]
     * values, or returns null when there is nothing to publish. A gauge without value in the period republishes its
     * latest value, since its level did not change.
     */
    private fun snapshot(level: RedisMeterLevel, normalized: Map<String, Any>?, timestamp: Instant, scope: String): MeterSnapshot? {
        (normalized?.get(VALUE_FIELD) as Double?)?.takeIf { level.meterId.type == MeterType.GAUGE }
            ?.let { latestGaugeValues[level.shortId] = it }
        return RedisMeterMeasurements.measurements(level, normalized, latestGaugeValues[level.shortId])?.let { measurements ->
            MeterSnapshotImpl(timestamp, level.meterId.copy(tags = level.meterId.tags + (SCOPE_TAG to scope)), measurements)
        }
    }

    /**
     * Declares the new meter levels, subscribes to the newly owned shards, drains the local meters and sends their
     * deltas to Redis with one concurrent call per shard. When a period ended since the latest flush, the period is
     * acknowledged to all the shards, even those without delta, and when [final] is true, the end of the campaign is
     * acknowledged.
     *
     * The declaration and the subscription failures are only logged: they are retried at the next flush.
     */
    @KTestable
    private suspend fun flush(final: Boolean) {
        tryAndLog("declare the meters") { declareLevels() }
        // The subscription precedes the first acknowledgement of the shard by this factory, hence the publication
        // of its first message.
        tryAndLog("subscribe to the meters of the owned shards") { subscribeToOwnedShards() }
        val currentPeriod = periodOf(System.currentTimeMillis())
        if (currentPeriod != latestPeriod) {
            // The ended period is acknowledged first, also when completing, so that its values are published.
            flush(latestPeriod, Acknowledgement.PERIOD)
            latestPeriod = currentPeriod
        }
        flush(currentPeriod, if (final) Acknowledgement.CAMPAIGN else Acknowledgement.NONE)
    }

    /**
     * Drains the meters and sends the deltas of [period] to the concerned shards, concurrently, with the
     * [acknowledgement] of the period or the campaign, if any.
     *
     * Without acknowledgement, only the primary levels having deltas are sent, to the shards containing them. With an
     * acknowledgement, all the declared levels are sent, and all the shards used by the campaign are called, so that
     * each shard receives the acknowledgement of every factory and can complete its barrier.
     */
    private suspend fun flush(period: Long, acknowledgement: Acknowledgement) {
        if (acknowledgement != Acknowledgement.NONE) {
            tryAndLog("refresh the shards of meters") { usedShards += setCommands.smembers(keys.shards).toList().map(String::toInt) }
        }
        meters.values.forEach { it.drain(complete = acknowledgement != Acknowledgement.NONE) }
        // When acknowledging, all the levels are sent, in order to also refresh the combined values of the idle ones.
        // Otherwise, only the levels read by the meters are sent: the others are only needed for the snapshots.
        val levelsByShard = levels.values
            .filter {
                it.isDeclared && (acknowledgement != Acknowledgement.NONE || (it.isPrimary && it.pending.isNotEmpty()))
            }
            .groupBy { it.shard }
        val recipients = if (acknowledgement == Acknowledgement.NONE) levelsByShard.keys else usedShards + levelsByShard.keys
        // Each shard has its own levels, the calls do not share any state and are executed concurrently.
        coroutineScope {
            recipients.forEach { shard ->
                launch { flushToShard(shard, levelsByShard[shard].orEmpty(), period, acknowledgement) }
            }
        }
    }

    /**
     * Declares the levels created since the previous call in the campaign registry, in a single call. Redis returns
     * for each level its short id and owner, assigned by the first factory declaring it, and the shards used by the
     * campaign. When the call fails, the levels are queued again for the next flush.
     */
    private suspend fun declareLevels() {
        val declaredLevels = generateSequence { undeclaredLevels.poll() }.toList()
        if (declaredLevels.isNotEmpty()) {
            try {
                val (declarations, campaignShards) = declarationScript.execute(
                    keys.registryKeys,
                    nodeId, registryTtl.seconds.toString(), shards.toString(), *declaredLevels.map { it.key }.toTypedArray()
                ).map { (it as List<*>).map(Any?::toString) }
                declaredLevels.zip(declarations).forEach { (level, declaration) ->
                    val (shortId, owner) = declaration.split(DECLARATION_SEPARATOR, limit = 2)
                    level.owner = owner
                    level.shard = (shortId.toLong() % shards).toInt()
                    level.shortId = shortId.toLong()
                    levelsByShortId[level.shortId] = level
                }
                usedShards += campaignShards.map(String::toInt)
            } catch (e: Exception) {
                undeclaredLevels += declaredLevels
                throw e
            }
        }
    }

    /**
     * Sends the pending deltas of the [levels] of [shard] in a single call, with the [acknowledgement] of [period],
     * and refreshes their cumulative values from the response. The histograms are sent as separate entries, flushed
     * less often than the other values. A level without delta sends deltas of 0, to receive its combined values.
     *
     * When the call fails, the deltas are added again to the pending values of the levels for the next flush.
     */
    private suspend fun flushToShard(shard: Int, levels: List<RedisMeterLevel>, period: Long, acknowledgement: Acknowledgement) {
        val deltas = levels.associateWith { level -> level.pending.toMap().ifEmpty { zeroDeltas(level) } }
        levels.forEach { it.pending.clear() }
        // The histograms are stored as separate entries, so that the frequent flushes of the other values stay light.
        val entries = HashMap<String, Any>()
        deltas.forEach { (level, values) ->
            val id = level.shortId.toString()
            entries[id] = values - BUCKETS_FIELD
            values[BUCKETS_FIELD]?.let { entries[id + RedisMeterMeasurements.BUCKETS_SUFFIX] = it }
        }
        try {
            val result = flushScript.execute(
                listOf(
                    keys.cumulative(shard), keys.previous(shard), keys.acknowledgements(shard, period),
                    keys.completions(shard), keys.message(shard, period)
                ),
                nodeId, factoriesCount.toString(), acknowledgement.code, period.toString(), keys.channel(shard),
                keysTtl.seconds.toString(), publishCommand, LogHistogram.LOG_GAMMA.toString(),
                (step.toMillis() * THROUGHPUT_RETAINED_PERIODS).toString(),
                objectMapper.writeValueAsString(entries)
            )
            val returned = objectMapper.readValue<Map<String, Map<String, Any?>>>(result[1].toString())
            RedisMeterMeasurements.byLevel(returned).forEach { (shortId, values) ->
                levelsByShortId[shortId]?.let { level ->
                    // The histogram is only returned when flushed, the latest known one is kept otherwise.
                    level.cumulative = if (BUCKETS_FIELD in values) values
                    else level.cumulative[BUCKETS_FIELD]?.let { values + (BUCKETS_FIELD to it) } ?: values
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deltas.forEach { (level, values) -> level.addPending(values) }
            log.error(e) { "The meters could not be flushed to the shard $shard: ${e.message}" }
        }
    }

    /**
     * Deltas changing nothing, in order to receive the combined values of [level]; the gauges also update their
     * value of the period.
     */
    private fun zeroDeltas(level: RedisMeterLevel): Map<String, Any> = when (level.meterId.type) {
        MeterType.COUNTER -> mapOf(COUNT_FIELD to 0.0)
        MeterType.GAUGE -> mapOf(VALUE_FIELD to 0.0)
        MeterType.RATE -> mapOf(NUMERATOR_FIELD to 0.0, DENOMINATOR_FIELD to 0.0)
        else -> mapOf(TOTAL_FIELD to 0.0)
    }

    /**
     * Reads the cumulative values of all the meter levels of [shard] from Redis, by short id, when the final message
     * of the shard was not received: some factories did not complete the campaign.
     */
    private suspend fun readCumulativeValues(shard: Int): Map<String, Map<String, Any?>> {
        log.warn {
            "The final meters of the campaign were not received within $finalTimeout, " +
                "some factories did not complete the campaign: reading them from Redis"
        }
        return hashCommands.hgetall(keys.cumulative(shard)).toList()
            .associate { it.key to objectMapper.readValue<Map<String, Any?>>(it.value) }
    }

    /**
     * End of the period of [step] containing [epochMillis], in epoch seconds: with a step of 5 s, 12:00:03.200 is in
     * the period ending at 12:00:05. All the factories compute the same periods from their clocks.
     */
    private fun periodOf(epochMillis: Long): Long {
        val stepMillis = step.toMillis()
        return (epochMillis / stepMillis + 1) * stepMillis / 1000
    }

    /**
     * Executes [block] and logs its failure as a failure to [action], without propagating it.
     */
    private inline fun tryAndLog(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.error(e) { "Failed to $action: ${e.message}" }
        }
    }

    /**
     * Acknowledgement sent with a flush, with its [code] passed to the flush script.
     */
    private enum class Acknowledgement(val code: String) {

        /**
         * Regular flush, without acknowledgement.
         */
        NONE("0"),

        /**
         * The factory completed the period: its values of the period were all sent.
         */
        PERIOD("1"),

        /**
         * The factory completed the campaign: no other value will be sent.
         */
        CAMPAIGN("2")
    }

    /**
     * Lua script loaded from the resource [path], executed by its SHA and loaded again when unknown to Redis, for
     * example after a restart of Redis or on a new node of a cluster.
     */
    private inner class Script(path: String) {

        /**
         * Source of the script.
         */
        private val source = RedisUtils.loadScript(path)

        /**
         * SHA of the script once loaded in Redis.
         */
        @Volatile
        private var sha: String? = null

        /**
         * Executes the script on [keys] with [arguments], and returns its result as a list.
         */
        suspend fun execute(keys: List<String>, vararg arguments: String): List<Any?> = try {
            evaluate(sha ?: load(), keys, arguments)
        } catch (e: RedisNoScriptException) {
            evaluate(load(), keys, arguments)
        }

        /**
         * Loads the script into Redis and keeps its SHA.
         */
        private suspend fun load() = scriptingCommands.scriptLoad(source)!!.also { sha = it }

        /**
         * Executes the script identified by [digest].
         */
        private suspend fun evaluate(digest: String, keys: List<String>, arguments: Array<out String>): List<Any?> =
            scriptingCommands.evalsha<List<Any?>>(digest, ScriptOutputType.MULTI, keys.toTypedArray(), *arguments)!!
    }

    private companion object {

        /**
         * Resource of the script declaring the meter levels.
         */
        const val DECLARATION_SCRIPT = "/redis/meters-declaration.lua"

        /**
         * Resource of the script flushing the deltas to a shard.
         */
        const val FLUSH_SCRIPT = "/redis/meters-flush.lua"

        /**
         * Tag of the meters identifying their scenario.
         */
        const val SCENARIO_TAG = "scenario"

        /**
         * Tag of the meters identifying their zone.
         */
        const val ZONE_TAG = "zone"

        /**
         * Tags removed from a meter to build its campaign level.
         */
        val SCENARIO_AND_ZONE_TAGS = setOf(SCENARIO_TAG, ZONE_TAG)

        /**
         * Tag of the snapshots giving their scope, [PERIOD_SCOPE] or [CAMPAIGN_SCOPE].
         */
        const val SCOPE_TAG = "scope"

        /**
         * Scope of the snapshots of a period.
         */
        const val PERIOD_SCOPE = "period"

        /**
         * Scope of the snapshots of the whole campaign.
         */
        const val CAMPAIGN_SCOPE = "campaign"

        /**
         * Separator of the short id and the owner in the declarations returned by Redis.
         */
        const val DECLARATION_SEPARATOR = '|'

        /**
         * Count of periods during which the hits of a throughput are kept by interval in Redis, before being folded.
         */
        const val THROUGHPUT_RETAINED_PERIODS = 2

        /**
         * Mapper of the JSON values exchanged with the Lua scripts and of the published messages.
         */
        val objectMapper = jacksonObjectMapper()

        val log = logger()
    }
}
