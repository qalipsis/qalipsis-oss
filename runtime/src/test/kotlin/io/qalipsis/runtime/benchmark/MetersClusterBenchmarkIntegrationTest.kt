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

package io.qalipsis.runtime.benchmark

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.redis.RedisClusterContainer
import io.qalipsis.runtime.Qalipsis
import io.qalipsis.runtime.bootstrap.QalipsisBootstrap
import io.qalipsis.runtime.deployments.AbstractDeploymentIntegrationTest
import io.qalipsis.runtime.test.JvmProcessUtils
import jdk.jfr.consumer.RecordingFile
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Benchmark of the meters on a real QALIPSIS cluster: a head and several factories running in their own JVM,
 * sharing a standalone Redis or a Redis Cluster. Only enabled with the environment variable
 * QALIPSIS_METERS_BENCHMARK=true.
 *
 * Each factory runs [MetersBenchmarkScenario.MINIONS] / factories minions; the reference is a single factory, whose
 * meters remain in memory. The factories are profiled with JFR, the Redis nodes with INFO and SLOWLOG.
 * The reports are written into build/reports/meters-benchmark.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@EnabledIfEnvironmentVariable(named = "QALIPSIS_METERS_BENCHMARK", matches = "true")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
internal class MetersClusterBenchmarkIntegrationTest : AbstractDeploymentIntegrationTest() {

    private val factoriesCount = System.getenv("BENCHMARK_FACTORIES")?.toIntOrNull() ?: 3

    private val reportDirectory = File("build/reports/meters-benchmark").apply { mkdirs() }

    @Test
    @Order(1)
    fun `single factory with in-memory meters`() {
        benchmark("single-factory-in-memory", 1, listOf(standaloneUri()))
    }

    @Test
    @Order(2)
    fun `several factories with a standalone Redis`() {
        benchmark("factories-redis-standalone", factoriesCount, listOf(standaloneUri()))
    }

    @Test
    @Order(3)
    fun `several factories with a Redis Cluster`() {
        benchmark("factories-redis-cluster", factoriesCount, CLUSTER.uris)
    }

    private fun benchmark(name: String, factories: Int, nodes: List<RedisURI>) {
        val nodeClients = nodes.map { RedisClient.create(it).connect().sync() }
        nodeClients.forEach {
            it.flushall()
            it.configResetstat()
            it.configSet("slowlog-log-slower-than", "0")
            it.configSet("slowlog-max-len", "50000")
            it.slowlogReset()
        }
        val redisConfiguration = if (nodes.size == 1) {
            arrayOf("-c", "redis.uri=${nodes.single()}")
        } else {
            arrayOf("-c", "redis.uris=${nodes.joinToString(",")}")
        }
        val headConfig = arrayOf(
            "head", "--autostart", "-e", "single-head",
            "-c", "campaign.required-factories=$factories",
            "-c", "campaign.start-offset=1s",
            "-c", "report.export.console.enabled=true",
            "-c", "report.export.junit.enabled=false",
        ) + redisConfiguration
        val factoryConfig = arrayOf(
            "factory", "-s", SCENARIO,
            "-c", "meters.redis.enabled=${System.getenv("BENCHMARK_REDIS_METERS") ?: "true"}",
            "-c", "meters.redis.shards=${System.getenv("BENCHMARK_SHARDS") ?: "16"}",
        ) + redisConfiguration
        val minionsPerFactory = MetersBenchmarkScenario.MINIONS / factoriesCount
        val totalMinions = minionsPerFactory * factories

        val output = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(TeeOutputStream(originalOut, output), true))
        val startedAt = System.currentTimeMillis()
        val (headCode, factoryProcesses) = try {
            val minionsFactor = totalMinions.toDouble() / MetersBenchmarkScenario.MINIONS
            val head = CompletableFuture.supplyAsync {
                QalipsisBootstrap().start(headConfig + arrayOf("-c", "campaign.minions-factor=$minionsFactor"))
            }
            val processes = (1..factories).map { index ->
                jvmProcessUtils.startNewJavaProcess(
                    Qalipsis::class,
                    arguments = factoryConfig,
                    jvmOptions = arrayOf(
                        "-Xmx1g",
                        "-XX:StartFlightRecording=settings=profile,dumponexit=true," +
                            "filename=${File(reportDirectory, "$name-factory-$index.jfr").absolutePath}"
                    ),
                    workingDirectory = Files.createTempDirectory("factory-$index").toFile()
                )
            }
            try {
                head.get(System.getenv("BENCHMARK_TIMEOUT_MINUTES")?.toLongOrNull() ?: 15, TimeUnit.MINUTES) to processes
            } catch (e: TimeoutException) {
                processes.forEachIndexed { index, process ->
                    File(reportDirectory, "$name-factory-${index + 1}.log").writeText(process.output + process.errorFile.readText())
                }
                throw e
            }
        } finally {
            System.setOut(originalOut)
        }
        val wallClock = Duration.ofMillis(System.currentTimeMillis() - startedAt)
        factoryProcesses.forEach { it.await(Duration.ofSeconds(30)) }

        // The head does not render a report without persistence: the duration is measured from its logs.
        val lines = output.toString().lines()
        fun timestampOf(marker: String) = lines.firstOrNull { marker in it }?.substringBefore(' ')
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val start = timestampOf("with scenarios [$SCENARIO]")
        val end = timestampOf("was successfully completed")
        val succeeded = end != null
        val duration = if (start != null && end != null) Duration.between(start, end) else wallClock
        val records = totalMinions.toLong() * MetersBenchmarkScenario.RECORDS_PER_MINION
        val seconds = duration.toMillis() / 1000.0
        val report = StringBuilder("# $name\n\n")
        report.append("Factories: $factories, minions: $totalMinions, ")
            .append("values per minion: ${MetersBenchmarkScenario.RECORDS_PER_MINION}, meter steps: $METER_STEPS, ")
            .appendLine("Redis nodes: ${nodes.size}, CPUs: ${Runtime.getRuntime().availableProcessors()}\n")
        report.append("| Head exit code | Successful | Wall clock (s) | Campaign duration (s) | Values | Values / s ")
            .appendLine("| Meter records / s | Meter records / s / factory |")
        report.appendLine("|---|---|---|---|---|---|---|---|")
        report.appendLine(
            "| %d | %s | %d | %.1f | %,d | %,.0f | %,.0f | %,.0f |".format(
                Locale.ROOT, headCode, succeeded, wallClock.seconds, seconds, records, records / seconds,
                records * METER_STEPS / seconds, records * METER_STEPS / seconds / factories
            )
        )
        report.appendServerStatistics(nodes, nodeClients)
        (1..factories).forEach { index ->
            report.append(summarizeProfile(File(reportDirectory, "$name-factory-$index.jfr"), "factory $index"))
        }
        File(reportDirectory, "$name.md").writeText(report.toString())
        println(report)
        factoryProcesses.forEachIndexed { index, process ->
            if (process.process.exitValue() != 0) {
                log.error { "Factory ${index + 1} OUTPUT: ${process.outputLines.takeLast(200).joinToString(separator = "\n\t")}" }
            }
        }
        nodeClients.forEach { it.statefulConnection.close() }
        check(headCode == 0 && succeeded) { "The campaign failed, head exit code: $headCode" }
    }

    private fun StringBuilder.appendServerStatistics(nodes: List<RedisURI>, nodeClients: List<RedisCommands<String, String>>) {
        appendLine("\n## Redis\n")
        appendLine("| Node | Commands | EVALSHA calls | EVALSHA µs/call | Net in (KiB) | Net out (KiB) | Used memory |")
        appendLine("|---|---|---|---|---|---|---|")
        val durations = mutableListOf<Long>()
        nodes.zip(nodeClients).forEach { (uri, node) ->
            val stats = (node.info("commandstats") + node.info("stats") + node.info("memory")).lines()
                .filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':').trim() }
            val evalsha = stats["cmdstat_evalsha"].orEmpty().split(',').filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            appendLine(
                "| %s:%d | %,d | %,d | %.1f | %,.0f | %,.0f | %s |".format(
                    Locale.ROOT, uri.host, uri.port, stats["total_commands_processed"]?.toLong() ?: 0,
                    evalsha["calls"]?.toLong() ?: 0, evalsha["usec_per_call"]?.toDouble() ?: 0.0,
                    (stats["total_net_input_bytes"]?.toDouble() ?: 0.0) / 1024, (stats["total_net_output_bytes"]?.toDouble() ?: 0.0) / 1024,
                    stats["used_memory_peak_human"]
                )
            )
            node.slowlogGet(50_000).forEach { entry ->
                val fields = entry as List<*>
                if ((fields[3] as List<*>).firstOrNull().toString().equals("EVALSHA", true)) {
                    durations += (fields[2] as Number).toLong()
                }
            }
        }
        durations.sort()
        if (durations.isNotEmpty()) {
            fun percentile(p: Double) = durations[((durations.size - 1) * p / 100).toInt()]
            appendLine("\nEVALSHA server duration (µs): count %,d, p50 %d, p95 %d, p99 %d, max %d, total %,.1f ms".format(
                Locale.ROOT, durations.size, percentile(50.0), percentile(95.0), percentile(99.0), durations.last(),
                durations.sum() / 1000.0
            ))
        }
    }

    /**
     * Summarizes the CPU samples of a factory, in particular the share of the meters registries.
     */
    private fun summarizeProfile(file: File, title: String): String {
        if (!file.exists()) {
            return "\n## JFR $title: no recording\n"
        }
        var samples = 0
        val activities = mutableMapOf<String, Int>()
        val topFrames = mutableMapOf<String, Int>()
        RecordingFile.readAllEvents(file.toPath()).filter { it.eventType.name == "jdk.ExecutionSample" }.forEach { event ->
            val frames = event.stackTrace?.frames.orEmpty().map { "${it.method.type.name}.${it.method.name}" }
            if (frames.isNotEmpty()) {
                samples++
                topFrames.merge(frames.first(), 1, Int::plus)
                val activity = when {
                    frames.any { it.startsWith("io.qalipsis.core.factory.redis.meters.RedisMeterRegistry") } ->
                        "Redis meters registry (flush, messages)"
                    frames.any { it.startsWith("io.qalipsis.core.factory.redis.meters") } -> "Redis meters (recording)"
                    frames.any { it.startsWith("io.qalipsis.core.factory.meters") } -> "in-memory meters and facade"
                    frames.any { it.startsWith("io.lettuce") } -> "Redis client (Lettuce)"
                    frames.any {
                        it.startsWith("io.qalipsis.core.factory.steps") || it.startsWith("io.qalipsis.core.factory.orchestration")
                    } -> "steps and orchestration"
                    else -> "other"
                }
                activities.merge(activity, 1, Int::plus)
            }
        }
        return buildString {
            appendLine("\n## JFR $title (${file.name}), $samples CPU samples\n")
            appendLine("| Activity | Share |\n|---|---|")
            activities.entries.sortedByDescending { it.value }.forEach { (activity, count) ->
                appendLine("| $activity | %.1f %% |".format(Locale.ROOT, 100.0 * count / samples))
            }
            appendLine("\n| Top CPU frames | Share |\n|---|---|")
            topFrames.entries.sortedByDescending { it.value }.take(8).forEach { (frame, count) ->
                appendLine("| $frame | %.1f %% |".format(Locale.ROOT, 100.0 * count / samples))
            }
        }
    }

    private class TeeOutputStream(private val first: PrintStream, private val second: ByteArrayOutputStream) : java.io.OutputStream() {

        override fun write(b: Int) {
            first.write(b)
            synchronized(second) { second.write(b) }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            first.write(b, off, len)
            synchronized(second) { second.write(b, off, len) }
        }
    }

    private fun standaloneUri() = RedisURI.create("redis://localhost:${STANDALONE.getMappedPort(REDIS_PORT)}")

    companion object {

        private const val REDIS_PORT = 6379

        /**
         * Scenario to execute, [MetersBenchmarkScenario] by default.
         */
        private val SCENARIO = System.getenv("BENCHMARK_SCENARIO") ?: "meters-benchmark"

        /**
         * Count of meter steps in the [MetersBenchmarkScenario].
         */
        private const val METER_STEPS = 6

        @JvmStatic
        @Container
        val STANDALONE: GenericContainer<*> = GenericContainer<Nothing>("redis:7-alpine").apply {
            withExposedPorts(REDIS_PORT)
            waitingFor(Wait.forListeningPort())
        }

        @JvmStatic
        @Container
        val CLUSTER = RedisClusterContainer()

        val log = logger()
    }
}
