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

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisScriptingCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisServerCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.mockk.mockk
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.redis.meters.catadioptre.flush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.ceil

/**
 * Integration tests of [RedisMeterRegistry] with several simulated factories sharing the same Redis, implemented
 * for a standalone Redis and a Redis Cluster.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractRedisMeterRegistryIntegrationTest {

    protected lateinit var redisClient: AbstractRedisClient

    protected lateinit var commands: RedisMeterTestCommands

    private lateinit var backgroundScope: CoroutineScope

    protected abstract fun createClient(): AbstractRedisClient

    protected abstract fun createCommands(client: AbstractRedisClient): RedisMeterTestCommands

    @BeforeAll
    fun connect() {
        redisClient = createClient()
        commands = createCommands(redisClient)
    }

    @AfterAll
    fun disconnect() {
        redisClient.shutdown()
    }

    @BeforeEach
    fun setUp() {
        backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterEach
    fun tearDown() = runBlocking {
        backgroundScope.cancel()
        commands.server.flushall()
        Unit
    }

    @Test
    fun `given several factories zones and scenarios when recording concurrently then all levels converge and are published once`() =
        runBlocking {
            // given
            val campaign = campaign(FACTORIES.size)
            val factories = FACTORIES.map { (nodeId, zone) -> SimulatedFactory(nodeId, zone, createRegistry(nodeId)) }
            factories.forEach { it.registry.init(campaign) }
            val periodSnapshots = ConcurrentLinkedQueue<MeterSnapshot>()
            val collector = launch(Dispatchers.Default) {
                // Simulates the periodic publication of the facade.
                while (isActive) {
                    factories.forEach { periodSnapshots += it.registry.snapshots(Instant.now()) }
                    delay(200)
                }
            }

            // when the meters are recorded over several periods.
            val deadline = System.currentTimeMillis() + RECORDING_DURATION.toMillis()
            factories.flatMap { factory ->
                SCENARIOS.map { scenario -> launch(Dispatchers.Default) { factory.record(scenario, deadline) } }
            }
                .joinAll()

            // then the combined values are read by all the factories sharing a meter.
            withTimeout(CONVERGENCE_TIMEOUT.toMillis()) {
                while (factories.any { factory ->
                        SCENARIOS.any { factory.counter(it).count() != expectedCount(factories, it, factory.zone) }
                    }) {
                    delay(50)
                }
            }

            // when
            val summaries = factories.map { async { it.registry.summarize(Instant.now()) } }.awaitAll().flatten()
            collector.cancel()
            periodSnapshots += summaries.filter { it.scope == PERIOD_SCOPE }

            // then each level is published once, by its owner, with the combined values.
            val campaignSnapshots = summaries.filter { it.scope == CAMPAIGN_SCOPE }
            assertThat(campaignSnapshots.groupBy { it.meterId }.values).each { it.hasSize(1) }
            assertThat(campaignSnapshots).hasSize(SCENARIOS.size * ZONES.size * METER_TYPES + SCENARIOS.size * METER_TYPES + METER_TYPES)
            SCENARIOS.forEach { scenario ->
                ZONES.forEach { zone ->
                    val tags = levelTags(scenario, zone)
                    assertThat(campaignSnapshots.value(MeterType.COUNTER, tags)).isEqualTo(expectedCount(factories, scenario, zone))
                    assertThat(campaignSnapshots.value(MeterType.GAUGE, tags)).isEqualTo(expectedGauge(factories, scenario, zone))
                    assertThat(campaignSnapshots.value(MeterType.RATE, tags)).isEqualTo(expectedRate(factories, scenario, zone))
                }
                val tags = levelTags(scenario)
                assertThat(campaignSnapshots.value(MeterType.COUNTER, tags)).isEqualTo(expectedCount(factories, scenario))
                assertThat(campaignSnapshots.value(MeterType.GAUGE, tags)).isEqualTo(expectedGauge(factories, scenario))
                assertThat(campaignSnapshots.value(MeterType.RATE, tags)).isEqualTo(expectedRate(factories, scenario))
            }
            assertThat(campaignSnapshots.value(MeterType.COUNTER, levelTags())).isEqualTo(expectedCount(factories))
            assertThat(campaignSnapshots.value(MeterType.GAUGE, levelTags())).isEqualTo(expectedGauge(factories))
            assertThat(campaignSnapshots.value(MeterType.RATE, levelTags())).isEqualTo(expectedRate(factories))
            (SCENARIOS.flatMap { scenario -> ZONES.map { levelTags(scenario, it) } + levelTags(scenario) } + levelTags()).forEach { tags ->
                val scenario = tags["scenario"]
                val zone = tags["zone"]
                val count = expectedCount(factories, scenario, zone)
                val values = expectedValues(factories, scenario, zone)
                val total = values.withIndex().sumOf { (index, records) -> (index + 1) * records.toDouble() }
                // Timers are recorded and published in microseconds.
                assertThat(campaignSnapshots.statistic(MeterType.TIMER, tags, Statistic.COUNT)).isEqualTo(count)
                assertThat(campaignSnapshots.statistic(MeterType.TIMER, tags, Statistic.TOTAL_TIME)).isEqualTo(total * 1000)
                assertThat(campaignSnapshots.statistic(MeterType.TIMER, tags, Statistic.MAX)).isEqualTo(DISTINCT_VALUES * 1000.0)
                assertThat(campaignSnapshots.statistic(MeterType.DISTRIBUTION_SUMMARY, tags, Statistic.COUNT)).isEqualTo(count)
                assertThat(campaignSnapshots.statistic(MeterType.DISTRIBUTION_SUMMARY, tags, Statistic.TOTAL)).isEqualTo(total)
                assertThat(campaignSnapshots.statistic(MeterType.DISTRIBUTION_SUMMARY, tags, Statistic.MAX))
                    .isEqualTo(DISTINCT_VALUES.toDouble())
                assertThat(campaignSnapshots.statistic(MeterType.THROUGHPUT, tags, Statistic.TOTAL)).isEqualTo(count)
                assertThat(campaignSnapshots.statistic(MeterType.THROUGHPUT, tags, Statistic.MAX)).isGreaterThan(0.0)
                PERCENTILES.forEach { percentile ->
                    val exact = exactPercentile(values, percentile)
                    assertThat(campaignSnapshots.percentile(MeterType.DISTRIBUTION_SUMMARY, tags, percentile))
                        .isBetween(exact * (1 - ACCURACY), exact * (1 + ACCURACY))
                    assertThat(campaignSnapshots.percentile(MeterType.TIMER, tags, percentile))
                        .isBetween(exact * 1000 * (1 - ACCURACY), exact * 1000 * (1 + ACCURACY))
                }
            }

            // then exactly one message per owner and period was received, and the step values sum up to the cumulative ones.
            val periods = periodSnapshots.toList()
            assertThat(periods.groupBy { it.meterId to it.timestamp }.values).each { it.hasSize(1) }
            assertThat(periods.map { it.timestamp }.distinct().size >= 2).isTrue()
            assertThat(periods.sum(MeterType.COUNTER, levelTags())).isEqualTo(expectedCount(factories))
            SCENARIOS.forEach { scenario ->
                assertThat(periods.sum(MeterType.COUNTER, levelTags(scenario))).isEqualTo(expectedCount(factories, scenario))
                ZONES.forEach { zone ->
                    assertThat(periods.sum(MeterType.COUNTER, levelTags(scenario, zone)))
                        .isEqualTo(expectedCount(factories, scenario, zone))
                }
            }

            // when
            factories.forEach { it.registry.close(campaign) }

            // then only keys with an expiry remain.
            assertThat(remainingKeys()).isEmpty()
        }

    @Test
    fun `given two campaigns running simultaneously when meters are recorded then their values are isolated`() = runBlocking {
        // given
        val campaign1 = campaign(2, "campaign-1")
        val campaign2 = campaign(2, "campaign-2")
        val factories1 = listOf("factory-1", "factory-2").map { SimulatedFactory(it, "eu", createRegistry(it), "campaign-1") }
        val factories2 = listOf("factory-1", "factory-2").map { SimulatedFactory(it, "eu", createRegistry(it), "campaign-2") }
        factories1.forEach { it.registry.init(campaign1) }
        factories2.forEach { it.registry.init(campaign2) }

        // when
        factories1.forEach { it.counter(SCENARIOS.first()).increment(3.0) }
        factories2.forEach { it.counter(SCENARIOS.first()).increment(11.0) }
        withTimeout(CONVERGENCE_TIMEOUT.toMillis()) {
            while (factories1.any { it.counter(SCENARIOS.first()).count() != 6.0 }
                || factories2.any { it.counter(SCENARIOS.first()).count() != 22.0 }) {
                delay(50)
            }
        }
        val summaries1 = factories1.map { async { it.registry.summarize(Instant.now()) } }.awaitAll().flatten()
        val summaries2 = factories2.map { async { it.registry.summarize(Instant.now()) } }.awaitAll().flatten()

        // then
        assertThat(summaries1.filter { it.scope == CAMPAIGN_SCOPE }.value(MeterType.COUNTER, levelTags(campaign = "campaign-1")))
            .isEqualTo(6.0)
        assertThat(summaries2.filter { it.scope == CAMPAIGN_SCOPE }.value(MeterType.COUNTER, levelTags(campaign = "campaign-2")))
            .isEqualTo(22.0)
        (factories1 + factories2).forEach { it.registry.close(campaign1) }
        assertThat(remainingKeys()).isEmpty()
    }

    @Test
    fun `given an owner when a factory does not complete the campaign then the cumulative values are read from Redis`() =
        runBlocking {
            // given
            val campaign = campaign(factoriesCount = 2)
            val factory1 = SimulatedFactory("factory-1", "eu", createRegistry("factory-1"))
            val factory2 = SimulatedFactory("factory-2", "us", createRegistry("factory-2"))
            factory1.registry.init(campaign)
            factory2.registry.init(campaign)
            val counter = factory1.counter(SCENARIOS.first())
            counter.increment(4.0)
            // Sends the value to Redis before the completion: the final flush, bounded by the final timeout, then has
            // nothing left to send, even on a slow host.
            factory1.registry.flush(final = false)

            // when only the first factory completes.
            val summary = factory1.registry.summarize(Instant.now())

            // then
            assertThat(summary.filter { it.scope == CAMPAIGN_SCOPE }.value(MeterType.COUNTER, levelTags())).isEqualTo(4.0)
            factory1.registry.close(campaign)
            factory2.registry.close(campaign)
        }

    /**
     * Keys remaining without expiry: the late acknowledgements and the fallback messages expire by themselves.
     */
    private suspend fun remainingKeys() = commands.key.keys("*").toList().filter { commands.key.ttl(it) == NO_EXPIRY }

    protected fun createRegistry(nodeId: String, step: Duration = STEP, linger: Duration = LINGER) = RedisMeterRegistry(
        factoryConfiguration = FactoryConfiguration().also {
            it.nodeId = nodeId
            it.tenant = TENANT
        },
        meterReporter = mockk(relaxed = true),
        redisClient = redisClient,
        scriptingCommands = commands.scripting,
        keyCommands = commands.key,
        setCommands = commands.set,
        hashCommands = commands.hash,
        backgroundScope = backgroundScope,
        step = step,
        configuration = RedisMetersConfiguration().also {
            it.linger = linger
            it.finalTimeout = Duration.ofSeconds(3)
            it.keysTtl = Duration.ofMinutes(1)
            it.registryTtl = Duration.ofMinutes(5)
        },
    )

    protected fun campaign(factoriesCount: Int, campaignKey: String = CAMPAIGN) = Campaign(
        campaignKey = campaignKey,
        broadcastChannel = "broadcast",
        feedbackChannel = "feedback",
        assignments = emptyList(),
        factoriesCount = factoriesCount
    )

    /**
     * Factory recording meters in all the scenarios, keeping track of the expected values.
     */
    protected class SimulatedFactory(
        val nodeId: String,
        val zone: String,
        val registry: RedisMeterRegistry,
        private val campaignKey: String = CAMPAIGN,
    ) {

        val increments = SCENARIOS.associateWith { AtomicLong() }

        val benchmarks = SCENARIOS.associateWith { AtomicLong() }

        val gaugeValue = (nodeId.substringAfterLast('-').toInt() * 10).toDouble()

        fun counter(scenario: String): Counter = registry.counter(meterId(MeterType.COUNTER, scenario))

        fun gauge(scenario: String): Gauge = registry.gauge(meterId(MeterType.GAUGE, scenario))

        fun rate(scenario: String): Rate = registry.rate(meterId(MeterType.RATE, scenario))

        fun timer(scenario: String): Timer = registry.timer(meterId(MeterType.TIMER, scenario), PERCENTILES)

        fun summary(scenario: String): DistributionSummary =
            registry.summary(meterId(MeterType.DISTRIBUTION_SUMMARY, scenario), PERCENTILES)

        fun throughput(scenario: String): Throughput =
            registry.throughput(meterId(MeterType.THROUGHPUT, scenario), ChronoUnit.SECONDS, PERCENTILES)

        /**
         * Count of records of each value from 1 to [DISTINCT_VALUES], by scenario.
         */
        val values = SCENARIOS.associateWith { AtomicLongArray(DISTINCT_VALUES) }

        suspend fun record(scenario: String, deadline: Long) {
            val counter = counter(scenario)
            val rate = rate(scenario)
            val timer = timer(scenario)
            val summary = summary(scenario)
            val throughput = throughput(scenario)
            gauge(scenario).set(gaugeValue)
            var iteration = 0L
            while (System.currentTimeMillis() < deadline) {
                val value = (iteration % DISTINCT_VALUES + 1).toInt()
                timer.record(Duration.ofMillis(value.toLong()))
                summary.record(value.toDouble())
                throughput.record()
                values.getValue(scenario).incrementAndGet(value - 1)
                counter.increment()
                rate.incrementTotal()
                if (iteration % 3 == 0L) {
                    rate.incrementBenchmark()
                    benchmarks.getValue(scenario).incrementAndGet()
                }
                increments.getValue(scenario).incrementAndGet()
                if (++iteration % 200 == 0L) {
                    delay(1)
                }
            }
        }

        private fun meterId(type: MeterType, scenario: String) =
            Meter.Id(METER, type, levelTags(scenario, zone, campaignKey))
    }

    private fun List<SimulatedFactory>.matching(scenario: String?, zone: String?) =
        filter { zone == null || it.zone == zone }.flatMap { factory ->
            SCENARIOS.filter { scenario == null || it == scenario }.map { factory to it }
        }

    private fun expectedCount(factories: List<SimulatedFactory>, scenario: String? = null, zone: String? = null) =
        factories.matching(scenario, zone).sumOf { (factory, s) -> factory.increments.getValue(s).get() }.toDouble()

    private fun expectedGauge(factories: List<SimulatedFactory>, scenario: String? = null, zone: String? = null) =
        factories.matching(scenario, zone).sumOf { (factory, _) -> factory.gaugeValue }

    private fun expectedValues(factories: List<SimulatedFactory>, scenario: String?, zone: String?) =
        LongArray(DISTINCT_VALUES) { index ->
            factories.matching(scenario, zone).sumOf { (factory, s) -> factory.values.getValue(s).get(index) }
        }

    private fun exactPercentile(values: LongArray, percentile: Double): Double {
        val rank = ceil(percentile / 100 * values.sum()).toLong()
        var cumulated = 0L
        return (values.indexOfFirst { cumulated += it; cumulated >= rank } + 1).toDouble()
    }

    private fun Collection<MeterSnapshot>.statistic(type: MeterType, tags: Map<String, String>, statistic: Statistic) =
        matching(type, tags, CAMPAIGN_SCOPE).single().measurements
            .single { it.statistic == statistic && it !is DistributionMeasurementMetric }.value

    private fun Collection<MeterSnapshot>.percentile(type: MeterType, tags: Map<String, String>, percentile: Double) =
        matching(type, tags, CAMPAIGN_SCOPE).single().measurements
            .single { it is DistributionMeasurementMetric && it.observationPoint == percentile }.value

    private fun expectedRate(factories: List<SimulatedFactory>, scenario: String? = null, zone: String? = null) =
        factories.matching(scenario, zone).sumOf { (factory, s) -> factory.benchmarks.getValue(s).get() }.toDouble() /
                expectedCount(factories, scenario, zone)

    private val MeterSnapshot.scope: String?
        get() = meterId.tags[SCOPE_TAG]

    private fun Collection<MeterSnapshot>.matching(type: MeterType, tags: Map<String, String>, scope: String) =
        filter { it.meterId.meterName == METER && it.meterId.type == type && it.meterId.tags == tags + (SCOPE_TAG to scope) }

    private fun Collection<MeterSnapshot>.value(type: MeterType, tags: Map<String, String>) =
        matching(type, tags, CAMPAIGN_SCOPE).also { assertThat(it).isNotEmpty() }.single().measurements.first().value

    private fun Collection<MeterSnapshot>.sum(type: MeterType, tags: Map<String, String>) =
        matching(type, tags, PERIOD_SCOPE).sumOf { (it.measurements.single() as MeasurementMetric).value }

    companion object {

        const val TENANT = "my-tenant"

        const val CAMPAIGN = "my-campaign"

        const val METER = "my-meter"

        const val SCOPE_TAG = "scope"

        const val PERIOD_SCOPE = "period"

        const val CAMPAIGN_SCOPE = "campaign"

        const val METER_TYPES = 6

        const val NO_EXPIRY = -1L

        const val DISTINCT_VALUES = 100

        val PERCENTILES = setOf(50.0, 99.0)

        val STEP: Duration = Duration.ofSeconds(1)

        val LINGER: Duration = Duration.ofMillis(100)

        val RECORDING_DURATION: Duration = Duration.ofMillis(2500)

        val CONVERGENCE_TIMEOUT: Duration = Duration.ofSeconds(15)

        /**
         * Tolerance on the percentiles, the relative accuracy of the histogram with a margin.
         */
        const val ACCURACY = 0.02

        val ZONES = listOf("eu", "us")

        val SCENARIOS = listOf("scenario-1", "scenario-2")

        /**
         * Two factories per zone.
         */
        val FACTORIES = listOf("factory-1" to "eu", "factory-2" to "eu", "factory-3" to "us", "factory-4" to "us")

        fun levelTags(scenario: String? = null, zone: String? = null, campaign: String = CAMPAIGN) =
            listOfNotNull(
                "campaign" to campaign,
                "step" to "my-step",
                scenario?.let { "scenario" to it },
                zone?.let { "zone" to it }
            ).toMap()
    }
}

/**
 * Redis commands used by the [RedisMeterRegistry] and the tests, from a standalone or a cluster connection.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class RedisMeterTestCommands(
    val scripting: RedisScriptingCoroutinesCommands<String, String>,
    val key: RedisKeyCoroutinesCommands<String, String>,
    val set: RedisSetCoroutinesCommands<String, String>,
    val hash: RedisHashCoroutinesCommands<String, String>,
    val server: RedisServerCoroutinesCommands<String, String>,
)
