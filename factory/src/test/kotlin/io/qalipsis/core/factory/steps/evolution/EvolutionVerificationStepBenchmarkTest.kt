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

package io.qalipsis.core.factory.steps.evolution

import io.mockk.coEvery
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.verification.EvolutionDirection
import io.qalipsis.core.factory.inmemory.InMemoryEvolutionCountsRepository
import io.qalipsis.test.steps.TestStepContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Duration
import java.util.Locale

/**
 * Measures the throughput of [EvolutionVerificationStep.execute], compared with a step only forwarding its input,
 * to isolate the cost of the verification. Enabled with the environment variable `QALIPSIS_STEPS_BENCHMARK=true`.
 */
@EnabledIfEnvironmentVariable(named = "QALIPSIS_STEPS_BENCHMARK", matches = "true")
internal class EvolutionVerificationStepBenchmarkTest {

    private val startStopContext = StepStartStopContext(
        campaignKey = "my-campaign",
        scenarioName = "my-scenario",
        dagId = "my-dag",
        stepName = "my-step",
        scheduledMinionCount = 1
    )

    @Test
    fun `throughput of the evolution verification`() = runBlocking {
        for (workers in listOf(1, 4, 8)) {
            for (keys in listOf(1, 10_000)) {
                val passThrough = measure(workers, keys) { PassThroughStep() }
                val verification = measure(workers, keys) { scope -> verificationStep(scope) }
                println(
                    String.format(
                        Locale.ROOT,
                        "workers=%d keys=%6d | pass-through %7.2f M records/s | verification %7.2f M records/s | " +
                                "verification cost %6.1f ns/record/worker",
                        workers, keys, passThrough.recordsPerSecond / 1e6, verification.recordsPerSecond / 1e6,
                        (verification.nanosPerRecordPerWorker - passThrough.nanosPerRecordPerWorker)
                    )
                )
            }
        }
    }

    private fun verificationStep(scope: CoroutineScope): EvolutionVerificationStep<Record> {
        val liveStateRegistry = mockk<CampaignReportLiveStateRegistry>(relaxed = true)
        coEvery { liveStateRegistry.put(any(), any(), any(), any(), any(), any()) } returns "message"
        @Suppress("UNCHECKED_CAST")
        return EvolutionVerificationStep(
            id = "my-step",
            retryPolicy = null,
            direction = EvolutionDirection.INCREASING,
            keyExtractor = { _, input -> input.key },
            valueExtractor = { input -> input.value as Comparable<Any> },
            expectedPercentile = 99.0,
            countsRepository = InMemoryEvolutionCountsRepository(),
            campaignReportLiveStateRegistry = liveStateRegistry,
            coroutineScope = scope,
            linger = Duration.ofSeconds(1)
        )
    }

    /**
     * Executes [RECORDS_PER_WORKER] records on each of the [workers] concurrently, after a warm-up, and returns the
     * best of [ROUNDS] rounds. The [keys] are shared by all the workers.
     */
    private suspend fun measure(workers: Int, keys: Int, stepFactory: (CoroutineScope) -> Step<Record, Record>): Result {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val step = stepFactory(scope)
        step.start(startStopContext)
        try {
            repeat(WARM_UP_ROUNDS) { round(step, workers, keys) }
            val bestNanos = (1..ROUNDS).minOf { round(step, workers, keys) }
            return Result(workers, bestNanos)
        } finally {
            step.stop(startStopContext)
            scope.cancel()
        }
    }

    private suspend fun round(step: Step<Record, Record>, workers: Int, keys: Int): Long =
        withContext(Dispatchers.Default) {
            val start = System.nanoTime()
            coroutineScope {
                val keyNames = Array(keys) { "key-$it" }
                repeat(workers) { worker ->
                    launch {
                        val input = Channel<Record>(1)
                        val context = TestStepContext<Record, Record>(
                            input = input,
                            output = Channel(Channel.CONFLATED),
                            minionId = "minion-$worker",
                            scenarioName = "my-scenario",
                            stepName = "my-step"
                        )
                        for (index in 0 until RECORDS_PER_WORKER) {
                            input.trySend(Record(keyNames[index % keys], index.toLong()))
                            step.execute(context)
                        }
                    }
                }
            }
            System.nanoTime() - start
        }

    private class Result(private val workers: Int, private val nanos: Long) {

        val recordsPerSecond get() = workers.toDouble() * RECORDS_PER_WORKER * 1e9 / nanos

        val nanosPerRecordPerWorker get() = nanos.toDouble() / RECORDS_PER_WORKER
    }

    private data class Record(val key: String, val value: Long)

    private class PassThroughStep : AbstractStep<Record, Record>("pass-through", null) {

        override suspend fun execute(context: StepContext<Record, Record>) {
            context.send(context.receive())
        }
    }

    private companion object {

        const val RECORDS_PER_WORKER = 2_000_000

        const val WARM_UP_ROUNDS = 2

        const val ROUNDS = 5
    }
}
