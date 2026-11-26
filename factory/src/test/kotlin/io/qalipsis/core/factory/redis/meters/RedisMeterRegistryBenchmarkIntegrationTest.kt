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

import io.qalipsis.core.redis.RedisClusterContainer
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.coroutines
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.coroutines
import io.mockk.mockk
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.meters.MeterRegistry
import io.qalipsis.core.factory.meters.inmemory.InMemoryCumulativeMeterRegistry
import io.qalipsis.core.redis.RedisRuntimeConfiguration
import jdk.jfr.Configuration
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.HdrHistogram.Histogram
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.system.measureTimeMillis

/**
 * Profiled benchmark of [RedisMeterRegistry] on a standalone Redis and a Redis Cluster, compared to the in-memory
 * registry. Only enabled with the environment variable QALIPSIS_METERS_BENCHMARK=true.
 *
 * Measures the recording throughput, the server-side duration of the Lua calls (SLOWLOG with a threshold of 0), the
 * count of commands and the network traffic per Redis node, the convergence lag of the combined values, and profiles
 * the CPU of the JVM with JFR. The reports are written into build/reports/meters-benchmark.
 *
 * The workload is configurable with the environment variables BENCHMARK_FACTORIES, BENCHMARK_METERS_PER_TYPE,
 * BENCHMARK_RECORDERS, BENCHMARK_DURATION_SECONDS, BENCHMARK_STEP_SECONDS, BENCHMARK_SHARDS.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
@Testcontainers
@EnabledIfEnvironmentVariable(named = "QALIPSIS_METERS_BENCHMARK", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class RedisMeterRegistryBenchmarkIntegrationTest {

    private val factoriesCount = env("BENCHMARK_FACTORIES", 4)

    private val metersPerType = env("BENCHMARK_METERS_PER_TYPE", 100)

    private val recordersPerFactory = env("BENCHMARK_RECORDERS", 4)

    private val duration = Duration.ofSeconds(env("BENCHMARK_DURATION_SECONDS", 30).toLong())

    private val step = Duration.ofSeconds(env("BENCHMARK_STEP_SECONDS", 5).toLong())

    private val linger = Duration.ofSeconds(1)

    private val shards = env("BENCHMARK_SHARDS", 16)

    private val reportDirectory = File("build/reports/meters-benchmark").apply { mkdirs() }

    @Test
    @Order(1)
    fun `in-memory baseline`() = runBlocking {
        val registries = (1..factoriesCount).map { InMemoryCumulativeMeterRegistry(mockk(relaxed = true)) }
        registries.forEach { it.init(campaign()) }
        val report = StringBuilder("# In-memory baseline\n\n").appendWorkload()
        val result = profiled("in-memory") { record(registries.mapIndexed { index, registry -> workload(index, registry) }) }
        report.appendLine("| Records | Records/s |").appendLine("|---|---|")
            .appendLine("| %,d | %,.0f |".format(Locale.ROOT, result.first.records, result.first.records / duration.seconds.toDouble()))
            .appendLine().append(result.second)
        write("in-memory", report)
    }

    @Test
    @Order(2)
    fun standalone() = runBlocking {
        val client = RedisClient.create(STANDALONE_URI())
        val commands = client.connect().coroutines().let { RedisMeterTestCommands(it, it, it, it, it) }
        benchmark("standalone", client, commands, listOf(STANDALONE_URI()))
        client.shutdown()
    }

    @Test
    @Order(3)
    fun cluster() = runBlocking {
        val client = CLUSTER.createClient()
        val commands = client.connect().coroutines().let { RedisMeterTestCommands(it, it, it, it, it) }
        benchmark("cluster", client, commands, CLUSTER.uris)
        client.shutdown()
    }

    /**
     * Throughput and duration of each meter operation for the in-memory registry and the Redis registry on a
     * standalone Redis and on a Redis Cluster, with the background flushes running, with 1 and several concurrent
     * callers.
     *
     * The percentiles are the ones of the average duration of a call over windows of [CALL_WINDOW] consecutive calls:
     * timing each call alone would mostly measure the clock, whose reads are slow and contend under concurrency on
     * some platforms.
     */
    @Test
    @Order(4)
    fun `meter calls`() = runBlocking {
        val inMemory = InMemoryCumulativeMeterRegistry(mockk(relaxed = true)).also { it.init(campaign()) }
        val results = linkedMapOf("In-memory" to callStats(workload(0, inMemory)))
        inMemory.close(campaign())
        results["Redis"] = redisCallStats(RedisClient.create(STANDALONE_URI()))
        results["Redis Cluster"] = redisCallStats(CLUSTER.createClient())

        val report = StringBuilder("# Meter calls\n\n")
            .append("Meters per type: ${SCENARIOS.size * metersPerType}, factories (Redis): $factoriesCount, ")
            .append("warm-up: ${CALL_WARMUP.toMillis()} ms, measure: ${CALL_MEASURE.toMillis()} ms, ")
            .appendLine("percentile window: $CALL_WINDOW calls, CPUs: ${Runtime.getRuntime().availableProcessors()}\n")
        report.appendCallTable("Throughput (million calls/s, all threads)", results) { "%.1f".format(Locale.ROOT, it.callsPerSecond / 1e6) }
        report.appendCallTable("p50 (ns per call)", results) { "%.1f".format(Locale.ROOT, it.p50) }
        report.appendCallTable("p99 (ns per call)", results) { "%.1f".format(Locale.ROOT, it.p99) }
        write("meter-calls", report)
    }

    /**
     * Measures the meter calls on the first of [factoriesCount] Redis registries connected with [client], all flushing.
     */
    private suspend fun redisCallStats(client: AbstractRedisClient): Map<Pair<String, Int>, CallStats> {
        val commands = when (client) {
            is RedisClusterClient -> client.connect().coroutines().let { RedisMeterTestCommands(it, it, it, it, it) }
            else -> (client as RedisClient).connect().coroutines().let { RedisMeterTestCommands(it, it, it, it, it) }
        }
        commands.server.flushall()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val campaign = campaign()
        val registries = (1..factoriesCount).map { index -> createRegistry("factory-$index", client, commands, scope) }
        registries.forEach { it.init(campaign) }
        val stats = callStats(workload(0, registries.first()))
        registries.forEach { it.close(campaign) }
        scope.cancel()
        client.shutdown()
        return stats
    }

    private fun StringBuilder.appendCallTable(
        title: String,
        results: Map<String, Map<Pair<String, Int>, CallStats>>,
        format: (CallStats) -> String,
    ) {
        appendLine("## $title\n")
        appendLine("| Operation | Threads | ${results.keys.joinToString(" | ")} |")
        appendLine("|---|---|${results.keys.joinToString("") { "---|" }}")
        results.values.first().keys.forEach { key ->
            appendLine("| ${key.first} | ${key.second} | ${results.values.joinToString(" | ") { format(it.getValue(key)) }} |")
        }
        appendLine()
    }

    /**
     * Measures each operation on the meters of [workload], spreading the calls over all the meters of the type, by
     * count of concurrent threads.
     */
    private fun callStats(workload: Workload): Map<Pair<String, Int>, CallStats> {
        val operations = linkedMapOf<String, (Int) -> Unit>(
            "Counter.increment()" to { i -> workload.counters[i % workload.counters.size].increment() },
            "Gauge.increment()" to { i -> workload.gauges[i % workload.gauges.size].increment() },
            "Gauge.set(v)" to { i -> workload.gauges[i % workload.gauges.size].set(i.toDouble()) },
            "Rate.incrementTotal()" to { i -> workload.rates[i % workload.rates.size].incrementTotal() },
            "Rate.incrementBenchmark()" to { i -> workload.rates[i % workload.rates.size].incrementBenchmark() },
            "Timer.record(d)" to { i -> workload.timers[i % workload.timers.size].record(Duration.ofMillis((i % 100 + 1).toLong())) },
            "DistributionSummary.record(v)" to { i -> workload.summaries[i % workload.summaries.size].record((i % 100 + 1).toDouble()) },
            "Throughput.record()" to { i -> workload.throughputs[i % workload.throughputs.size].record() },
            "Counter.count()" to { i -> workload.counters[i % workload.counters.size].count() },
            "Timer.percentile(99)" to { i -> workload.timers[i % workload.timers.size].percentile(99.0, TimeUnit.MILLISECONDS) },
            "Throughput.current()" to { i -> workload.throughputs[i % workload.throughputs.size].current() },
        )
        val result = linkedMapOf<Pair<String, Int>, CallStats>()
        operations.forEach { (name, operation) ->
            CALL_THREADS.forEach { threads ->
                measureCalls(threads, operation, CALL_WARMUP)
                result[name to threads] = measureCalls(threads, operation, CALL_MEASURE)
            }
        }
        return result
    }

    /**
     * Calls [operation] from [threads] threads during [duration], timing each window of [CALL_WINDOW] calls.
     */
    private fun measureCalls(threads: Int, operation: (Int) -> Unit, duration: Duration): CallStats {
        val calls = AtomicLong()
        val windows = Histogram(3).apply { setAutoResize(true) }
        val start = System.nanoTime()
        (0 until threads).map { thread ->
            Thread {
                val local = Histogram(3).apply { setAutoResize(true) }
                var i = thread * 7919
                var count = 0L
                val end = System.nanoTime() + duration.toNanos()
                var now = System.nanoTime()
                while (now < end) {
                    repeat(CALL_WINDOW) { operation(i++) }
                    val windowEnd = System.nanoTime()
                    local.recordValue(windowEnd - now)
                    count += CALL_WINDOW
                    now = windowEnd
                }
                calls.addAndGet(count)
                synchronized(windows) { windows.add(local) }
            }.apply { start() }
        }.forEach(Thread::join)
        val elapsedSeconds = (System.nanoTime() - start) / 1e9
        return CallStats(
            callsPerSecond = calls.get() / elapsedSeconds,
            p50 = windows.getValueAtPercentile(50.0).toDouble() / CALL_WINDOW,
            p99 = windows.getValueAtPercentile(99.0).toDouble() / CALL_WINDOW,
        )
    }

    private data class CallStats(val callsPerSecond: Double, val p50: Double, val p99: Double)

    private suspend fun benchmark(mode: String, client: AbstractRedisClient, commands: RedisMeterTestCommands, nodes: List<RedisURI>) {
        val nodeClients = nodes.map { RedisClient.create(it).connect().sync() }
        commands.server.flushall()
        nodeClients.forEach {
            it.configResetstat()
            it.configSet("slowlog-log-slower-than", "0")
            it.configSet("slowlog-max-len", "50000")
            it.slowlogReset()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val campaign = campaign()
        val registries = (1..factoriesCount).map { index -> createRegistry("factory-$index", client, commands, scope) }
        registries.forEach { it.init(campaign) }

        val report = StringBuilder("# Redis $mode\n\n").appendWorkload()
        val (outcome, profile) = profiled(mode) {
            val workloads = registries.mapIndexed { index, registry -> workload(index, registry) }
            val recording = record(workloads)
            // Convergence: all the factories read the combined values of their zone + scenario counters.
            val expected = expectedCounts(workloads)
            val convergence = measureTimeMillis {
                withTimeoutOrNull(step.toMillis() * 4) {
                    while (workloads.any { w -> w.counters.any { it.count() != expected.getValue(it.id) } }) {
                        delay(20)
                    }
                }
            }
            val mismatches = workloads.flatMapIndexed { index, w ->
                w.counters.filter { it.count() != expected.getValue(it.id) }.map {
                    "factory-${index + 1} ${it.id.tags["scenario"]} ${it.id.meterName}: " +
                        "${it.count()} instead of ${expected.getValue(it.id)}"
                }
            }
            if (mismatches.isNotEmpty()) {
                val counter = workloads.flatMap { it.counters }.first { it.count() != expected.getValue(it.id) } as RedisCounter
                val level = counter.levels.first()
                val keys = RedisMeterKeys("benchmark", CAMPAIGN, shards)
                val inRedis = commands.hash.hget(keys.cumulative(level.shard), "${level.shortId}")
                error(
                    "${mismatches.size} counters did not converge, e.g. ${mismatches.take(3)}; first one in Redis: $inRedis, " +
                        "cached: ${level.cumulative}, expected ${expected.getValue(counter.id)}"
                )
            }
            var summaries: List<MeterSnapshot>
            val summarizeDuration = measureTimeMillis {
                summaries = registries.map { scope.async { it.summarize(Instant.now()) } }.awaitAll().flatten()
            }
            BenchmarkOutcome(recording.records, convergence, summarizeDuration, summaries, workloads)
        }
        registries.forEach { it.close(campaign) }
        scope.cancel()

        // Correctness: the campaign level of each counter sums all the increments.
        val campaignCounters = outcome.summaries.filter {
            it.meterId.type == MeterType.COUNTER && it.meterId.tags["scope"] == "campaign" && "scenario" !in it.meterId.tags
        }
        assertThat(campaignCounters.sumOf { it.measurements.single().value })
            .isEqualTo(outcome.workloads.sumOf { it.counterIncrements.get() }.toDouble())

        report.appendLine("## Client side\n")
            .appendLine("| Records | Records/s | Convergence lag (ms) | Summarize (ms) | Published campaign snapshots |")
            .appendLine("|---|---|---|---|---|")
            .appendLine(
                "| %,d | %,.0f | %d | %d | %d |".format(Locale.ROOT, 
                    outcome.records, outcome.records / duration.seconds.toDouble(), outcome.convergence,
                    outcome.summarizeDuration, outcome.summaries.count { it.meterId.tags["scope"] == "campaign" }
                )
            )
            .appendLine()
        report.appendServerStatistics(nodes, nodeClients)
        report.appendLine().append(profile)
        write(mode, report)
        nodeClients.forEach { it.statefulConnection.close() }
    }

    private fun StringBuilder.appendWorkload() = apply {
        append("Factories: $factoriesCount, scenarios: ${SCENARIOS.size}, zones: ${ZONES.size}, ")
        appendLine("meters per type and scenario: $metersPerType (counters, gauges, rates, timers, summaries, throughputs)")
        append("Recorders per factory: $recordersPerFactory, shards: $shards, duration: ${duration.seconds} s, ")
        appendLine("period: ${step.seconds} s, linger: ${linger.seconds} s")
        append("Meters per factory: ${SCENARIOS.size * metersPerType * METER_TYPES}, ")
        appendLine("CPUs: ${Runtime.getRuntime().availableProcessors()}, JVM: ${System.getProperty("java.version")}")
        appendLine()
    }

    private fun StringBuilder.appendServerStatistics(
        nodes: List<RedisURI>,
        nodeClients: List<io.lettuce.core.api.sync.RedisCommands<String, String>>,
    ) {
        appendLine("## Server side\n")
        appendLine("| Node | Commands | EVALSHA calls | EVALSHA µs/call | SMEMBERS calls | Net in (KiB) | Net out (KiB) |")
        appendLine("|---|---|---|---|---|---|---|")
        val durations = mutableMapOf<String, MutableList<Long>>()
        nodes.zip(nodeClients).forEach { (uri, node) ->
            val stats = parseInfo(node.info("commandstats") + node.info("stats"))
            val evalsha = commandStats(stats["cmdstat_evalsha"])
            val smembers = commandStats(stats["cmdstat_smembers"])
            appendLine(
                "| %s:%d | %,d | %,d | %.1f | %,d | %,.0f | %,.0f |".format(Locale.ROOT, 
                    uri.host, uri.port, stats["total_commands_processed"]?.toLong() ?: 0, evalsha["calls"]?.toLong() ?: 0,
                    evalsha["usec_per_call"]?.toDouble() ?: 0.0, smembers["calls"]?.toLong() ?: 0,
                    (stats["total_net_input_bytes"]?.toDouble() ?: 0.0) / 1024, (stats["total_net_output_bytes"]?.toDouble() ?: 0.0) / 1024
                )
            )
            node.slowlogGet(50_000).forEach { entry ->
                val fields = entry as List<*>
                val micros = (fields[2] as Number).toLong()
                val arguments = (fields[3] as List<*>).map { it.toString() }
                if (arguments.firstOrNull().equals("EVALSHA", true)) {
                    durations.getOrPut(classify(arguments)) { mutableListOf() } += micros
                }
            }
        }
        appendLine("\n### Duration of the Lua calls on the server (µs)\n")
        appendLine("| Call | Count | p50 | p95 | p99 | max | total (ms) |")
        appendLine("|---|---|---|---|---|---|---|")
        durations.toSortedMap().forEach { (call, values) ->
            values.sort()
            appendLine(
                "| %s | %,d | %d | %d | %d | %d | %,.1f |".format(Locale.ROOT, 
                    call, values.size, values.percentile(50.0), values.percentile(95.0), values.percentile(99.0), values.last(),
                    values.sum() / 1000.0
                )
            )
        }
    }

    /**
     * Classifies an EVALSHA call from its arguments: EVALSHA, sha, count of keys, keys..., then the arguments.
     */
    private fun classify(arguments: List<String>): String {
        val keysCount = arguments[2].toInt()
        return if (keysCount == 3) "declaration" else when (arguments.getOrNull(3 + keysCount + 2)) {
            "0" -> "flush"
            "1" -> "flush + period acknowledgement"
            "2" -> "flush + campaign completion"
            else -> "other"
        }
    }

    private fun List<Long>.percentile(percentile: Double) = this[((size - 1) * percentile / 100).toInt()]

    private fun parseInfo(info: String) =
        info.lines().filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':').trim() }

    private fun commandStats(value: String?) =
        value.orEmpty().split(',').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

    /**
     * Executes [block] while recording the JVM with JFR, and returns its result with a summary of the CPU samples.
     */
    private suspend fun <T> profiled(name: String, block: suspend () -> T): Pair<T, String> {
        val file = File(reportDirectory, "$name.jfr")
        val recording = Recording(Configuration.getConfiguration("profile")).apply { start() }
        val start = Instant.now()
        val result = try {
            block()
        } finally {
            recording.stop()
            recording.dump(file.toPath())
            recording.close()
        }
        return result to summarizeProfile(file, start, Instant.now())
    }

    /**
     * Summarizes the CPU samples and allocations of the profile, only considering the events between [start] and
     * [end], since a dump contains complete chunks, possibly shared with previous recordings.
     */
    private fun summarizeProfile(file: File, start: Instant, end: Instant): String {
        val activities = mutableMapOf<String, Int>()
        val topFrames = mutableMapOf<String, Int>()
        val packages = mutableMapOf<String, Int>()
        var samples = 0
        var allocatedBytes = 0L
        val allocations = mutableMapOf<String, Long>()
        RecordingFile.readAllEvents(file.toPath()).filter { it.startTime in start..end }.forEach { event ->
            when (event.eventType.name) {
                "jdk.ExecutionSample" -> {
                    val frames = event.stackTrace?.frames.orEmpty()
                    if (frames.isNotEmpty()) {
                        samples++
                        val top = frames.first().method
                        topFrames.merge("${top.type.name}.${top.name}", 1, Int::plus)
                        PROFILED_PACKAGES.firstOrNull { pkg -> frames.any { it.method.type.name.startsWith(pkg) } }
                            ?.let { packages.merge(it, 1, Int::plus) }
                        activities.merge(activity(frames.map { "${it.method.type.name}.${it.method.name}" }), 1, Int::plus)
                    }
                }

                "jdk.ObjectAllocationInNewTLAB", "jdk.ObjectAllocationOutsideTLAB" -> {
                    val weight = if (event.hasField("tlabSize")) event.getLong("tlabSize") else event.getLong("allocationSize")
                    allocatedBytes += weight
                    event.stackTrace?.frames?.firstOrNull { frame -> PROFILED_PACKAGES.any { frame.method.type.name.startsWith(it) } }
                        ?.let { allocations.merge("${it.method.type.name}.${it.method.name}", weight, Long::plus) }
                }
            }
        }
        return buildString {
            appendLine("## JVM profile (JFR, ${file.name})\n")
            appendLine("CPU samples: $samples, sampled allocations: %,.1f MiB\n".format(Locale.ROOT, allocatedBytes / 1024.0 / 1024))
            appendLine("| Activity of the CPU samples | Share |\n|---|---|")
            activities.entries.sortedByDescending { it.value }.forEach { (activity, count) ->
                appendLine("| $activity | %.1f %% |".format(Locale.ROOT, 100.0 * count / samples))
            }
            appendLine("\n| Samples containing a frame of | Share |\n|---|---|")
            packages.entries.sortedByDescending { it.value }.forEach { (pkg, count) ->
                appendLine("| $pkg | %.1f %% |".format(Locale.ROOT, 100.0 * count / samples))
            }
            appendLine("\n| Top CPU frames | Share |\n|---|---|")
            topFrames.entries.sortedByDescending { it.value }.take(15).forEach { (frame, count) ->
                appendLine("| $frame | %.1f %% |".format(Locale.ROOT, 100.0 * count / samples))
            }
            appendLine("\n| Top allocating QALIPSIS / Lettuce frames | MiB |\n|---|---|")
            allocations.entries.sortedByDescending { it.value }.take(10).forEach { (frame, bytes) ->
                appendLine("| $frame | %,.1f |".format(Locale.ROOT, bytes / 1024.0 / 1024))
            }
        }
    }

    /**
     * Classifies a CPU sample by the activity of its thread, from its frames, the top one first.
     */
    private fun activity(frames: List<String>) = when {
        frames.any { "RedisMeterRegistry.receive" in it } -> "meters registry: reception of the messages"
        frames.any { "RedisMeterRegistry.flush" in it || "RedisMeterRegistry.declare" in it } -> "meters registry: flush to Redis"
        frames.any { it.startsWith("io.lettuce") || it.startsWith("io.netty") } -> "Redis client (Lettuce / Netty)"
        frames.any { "BenchmarkIntegrationTest" in it } -> "recording of the values (workload)"
        frames.any { it.startsWith("jdk.jfr") || it.startsWith("jdk.internal.jfr") } -> "JFR"
        else -> "other"
    }

    private fun write(name: String, report: CharSequence) {
        File(reportDirectory, "$name.md").writeText(report.toString())
        println(report)
    }

    private class Workload(
        val counters: List<Counter>,
        val gauges: List<Gauge>,
        val rates: List<Rate>,
        val timers: List<Timer>,
        val summaries: List<DistributionSummary>,
        val throughputs: List<Throughput>,
        val counterIncrements: AtomicLong = AtomicLong(),
    ) {
        val incrementsPerCounter = AtomicLongArray(counters.size)
    }

    private data class RecordingResult(val records: Long)

    private class BenchmarkOutcome(
        val records: Long,
        val convergence: Long,
        val summarizeDuration: Long,
        val summaries: List<MeterSnapshot>,
        val workloads: List<Workload>,
    )

    private fun workload(index: Int, registry: MeterRegistry): Workload {
        val zone = ZONES[index % ZONES.size]
        fun id(type: MeterType, scenario: String, meter: Int) =
            Meter.Id("meter-$meter", type, mapOf("campaign" to CAMPAIGN, "scenario" to scenario, "zone" to zone, "step" to "step-$meter"))
        return Workload(
            counters = SCENARIOS.flatMap { s -> (1..metersPerType).map { registry.counter(id(MeterType.COUNTER, s, it)) } },
            gauges = SCENARIOS.flatMap { s -> (1..metersPerType).map { registry.gauge(id(MeterType.GAUGE, s, it)) } },
            rates = SCENARIOS.flatMap { s -> (1..metersPerType).map { registry.rate(id(MeterType.RATE, s, it)) } },
            timers = SCENARIOS.flatMap { s -> (1..metersPerType).map { registry.timer(id(MeterType.TIMER, s, it), PERCENTILES) } },
            summaries = SCENARIOS.flatMap { s ->
                (1..metersPerType).map { registry.summary(id(MeterType.DISTRIBUTION_SUMMARY, s, it), PERCENTILES) }
            },
            throughputs = SCENARIOS.flatMap { s ->
                (1..metersPerType).map { registry.throughput(id(MeterType.THROUGHPUT, s, it), ChronoUnit.SECONDS, PERCENTILES) }
            },
        )
    }

    /**
     * Records on all the meters in a round-robin, as fast as possible, from several coroutines per factory.
     */
    private suspend fun record(workloads: List<Workload>): RecordingResult {
        val records = AtomicLong()
        val deadline = System.currentTimeMillis() + duration.toMillis()
        val scope = CoroutineScope(Dispatchers.Default)
        workloads.flatMap { workload ->
            (0 until recordersPerFactory).map { recorder ->
                scope.launch {
                    var iteration = recorder.toLong()
                    var localRecords = 0L
                    var localIncrements = 0L
                    val incrementsPerCounter = LongArray(workload.counters.size)
                    while (System.currentTimeMillis() < deadline) {
                        repeat(BATCH) {
                            val meter = (iteration % workload.counters.size).toInt()
                            when (iteration % METER_TYPES) {
                                0L -> workload.counters[meter].increment().also {
                                    localIncrements++
                                    incrementsPerCounter[meter]++
                                }
                                1L -> workload.gauges[meter].increment()
                                2L -> workload.rates[meter].apply { incrementTotal(); if (iteration % 2 == 0L) incrementBenchmark() }
                                3L -> workload.timers[meter].record(Duration.ofMillis(iteration % 100 + 1))
                                4L -> workload.summaries[meter].record((iteration % 100 + 1).toDouble())
                                else -> workload.throughputs[meter].record()
                            }
                            iteration += recordersPerFactory
                        }
                        localRecords += BATCH
                        yield()
                    }
                    records.addAndGet(localRecords)
                    workload.counterIncrements.addAndGet(localIncrements)
                    incrementsPerCounter.forEachIndexed { index, count -> workload.incrementsPerCounter.addAndGet(index, count) }
                }
            }
        }.joinAll()
        return RecordingResult(records.get())
    }

    /**
     * The factories of a zone record the same meters: the combined value of a meter is the sum of the local increments.
     */
    private fun expectedCounts(workloads: List<Workload>): Map<Meter.Id, Double> =
        workloads.flatMap { workload ->
            workload.counters.mapIndexed { index, counter -> counter.id to workload.incrementsPerCounter[index] }
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, increments) -> increments.sum().toDouble() }

    private fun createRegistry(nodeId: String, client: AbstractRedisClient, commands: RedisMeterTestCommands, scope: CoroutineScope) =
        RedisMeterRegistry(
            factoryConfiguration = FactoryConfiguration().also {
                it.nodeId = nodeId
                it.tenant = "benchmark"
            },
            meterReporter = mockk(relaxed = true),
            redisClient = client,
            scriptingCommands = commands.scripting,
            keyCommands = commands.key,
            setCommands = commands.set,
            hashCommands = commands.hash,
            backgroundScope = scope,
            step = step,
            configuration = RedisMetersConfiguration().also {
                it.linger = linger
                it.finalTimeout = Duration.ofSeconds(30)
                it.keysTtl = Duration.ofMinutes(5)
                it.registryTtl = Duration.ofHours(1)
                it.shards = shards
            },
        )

    private fun campaign() = Campaign(
        campaignKey = CAMPAIGN,
        broadcastChannel = "broadcast",
        feedbackChannel = "feedback",
        assignments = emptyList(),
        factoriesCount = factoriesCount
    )

    private fun env(name: String, default: Int) = System.getenv(name)?.toIntOrNull() ?: default

    companion object {

        const val CAMPAIGN = "benchmark-campaign"

        val CALL_THREADS = listOf(1, 8)

        val CALL_WARMUP: Duration = Duration.ofSeconds(1)

        val CALL_MEASURE: Duration = Duration.ofSeconds(2)

        const val CALL_WINDOW = 100

        const val BATCH = 1_000

        const val METER_TYPES = 6

        val PERCENTILES = setOf(50.0, 99.0)

        val ZONES = listOf("eu", "us")

        val SCENARIOS = listOf("scenario-1", "scenario-2")

        val PROFILED_PACKAGES = listOf("io.qalipsis.core.factory.redis.meters", "io.lettuce", "io.netty", "kotlinx.coroutines")

        @JvmStatic
        @Container
        val STANDALONE: GenericContainer<*> = GenericContainer<Nothing>("redis:7-alpine").apply {
            withExposedPorts(RedisRuntimeConfiguration.DEFAULT_PORT)
            waitingFor(Wait.forListeningPort())
        }

        @JvmStatic
        @Container
        val CLUSTER = RedisClusterContainer()

        val STANDALONE_URI = { RedisURI.create("redis://localhost:${STANDALONE.getMappedPort(RedisRuntimeConfiguration.DEFAULT_PORT)}") }
    }
}
