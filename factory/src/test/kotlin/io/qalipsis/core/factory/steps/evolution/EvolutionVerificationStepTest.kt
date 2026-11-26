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

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.aerisconsulting.catadioptre.getProperty
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.verification.EvolutionDirection
import io.qalipsis.test.steps.StepTestHelper.createStepContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@ExtendWith(MockKExtension::class)
internal class EvolutionVerificationStepTest {

    @MockK
    private lateinit var countsRepository: EvolutionCountsRepository

    @RelaxedMockK
    private lateinit var liveStateRegistry: CampaignReportLiveStateRegistry

    private val context = StepStartStopContext(
        campaignKey = CAMPAIGN,
        scenarioName = SCENARIO,
        dagId = "my-dag",
        stepName = STEP,
        scheduledMinionCount = 1
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        coJustRun { countsRepository.register(any(), any(), any()) }
        coEvery { liveStateRegistry.put(any(), any(), any(), any(), any(), any()) } returns MESSAGE_ID
    }

    @Test
    fun `given increasing values with equal ones when the last instance stops then the records are forwarded and INFO is reported`() =
        runTest {
            val step = step(backgroundScope)
            completeAs(100.0, last = true)

            step.start(context)
            step.execute(Entity("a", 1), Entity("a", 1), Entity("a", 2), Entity("a", 5))
            step.stop(context)

            coVerifyOrder {
                countsRepository.register(CAMPAIGN, SCENARIO, STEP)
                countsRepository.unregister(CAMPAIGN, SCENARIO, STEP, 3, 0)
                liveStateRegistry.put(
                    CAMPAIGN, SCENARIO, STEP, ReportMessageSeverity.INFO, null,
                    "100.00% of the transitions are increasing (expected at least 99.00%)"
                )
            }
            confirmVerified(countsRepository, liveStateRegistry)
        }

    @Test
    fun `given decreasing values of several keys when stopping then the transitions of each key are counted`() =
        runTest {
            val step = step(backgroundScope, direction = EvolutionDirection.DECREASING)
            completeAs(60.0, last = false)

            step.start(context)
            step.execute(
                Entity("a", 5), Entity("b", 1), Entity("a", 3), Entity("b", 2), Entity("a", 4), Entity("b", 2)
            )
            step.stop(context)

            // a: 5 -> 3 valid, 3 -> 4 invalid; b: 1 -> 2 invalid, 2 -> 2 valid.
            coVerify { countsRepository.unregister(CAMPAIGN, SCENARIO, STEP, 2, 2) }
        }

    @Test
    fun `given null keys, null values and single values per key when the last instance stops then no transition is reported`() =
        runTest {
            val step = step(backgroundScope)
            completeAs(null, last = true)

            step.start(context)
            step.execute(Entity(null, 1), Entity("a", null), Entity("a", 1), Entity("b", 0), Entity(null, 2))
            step.stop(context)

            coVerifyOrder {
                countsRepository.unregister(CAMPAIGN, SCENARIO, STEP, 0, 0)
                liveStateRegistry.put(
                    CAMPAIGN, SCENARIO, STEP, ReportMessageSeverity.INFO, null,
                    "No transition was recorded, the increasing evolution could not be verified"
                )
            }
        }

    @Test
    fun `given a percentage below the expectation when the last instance stops then ERROR is reported`() = runTest {
        val step = step(backgroundScope)
        completeAs(98.5, last = true)
        step.setProperty("messageId", "previous-message")

        step.start(context)
        step.stop(context)

        coVerify {
            liveStateRegistry.put(
                CAMPAIGN, SCENARIO, STEP, ReportMessageSeverity.ERROR, "previous-message",
                "Only 98.50% of the transitions are increasing, expected at least 99.00%"
            )
        }
    }

    @Test
    fun `given an instance that is not the last when it stops then it deletes its live message only`() = runTest {
        val step = step(backgroundScope)
        completeAs(50.0, last = false)
        step.setProperty("messageId", "previous-message")

        step.start(context)
        step.stop(context)

        coVerify { liveStateRegistry.delete(CAMPAIGN, SCENARIO, STEP, "previous-message") }
        confirmVerified(liveStateRegistry)
        assertThat(step.getProperty<String?>("messageId")).isNull()
    }

    @Test
    fun `given pending transitions when the linger elapses then they are flushed and the live message put then removed`() =
        runTest {
            val step = step(backgroundScope)
            coEvery { countsRepository.increment(CAMPAIGN, SCENARIO, STEP, any(), any()) } returnsMany listOf(90.0, 99.5)
            step.start(context)

            step.execute(Entity("a", 2), Entity("a", 1), Entity("a", 3))
            advanceTimeBy(LINGER.toMillis() + 1)
            step.execute(Entity("a", 4))
            advanceTimeBy(LINGER.toMillis())
            // Without pending transition, the linger does not call the repository.
            advanceTimeBy(LINGER.toMillis())

            coVerifyOrder {
                countsRepository.register(CAMPAIGN, SCENARIO, STEP)
                countsRepository.increment(CAMPAIGN, SCENARIO, STEP, 1, 1)
                liveStateRegistry.put(
                    CAMPAIGN, SCENARIO, STEP, ReportMessageSeverity.ERROR, null,
                    "Only 90.00% of the transitions are increasing, expected at least 99.00%"
                )
                countsRepository.increment(CAMPAIGN, SCENARIO, STEP, 1, 0)
                liveStateRegistry.delete(CAMPAIGN, SCENARIO, STEP, MESSAGE_ID)
            }
            confirmVerified(countsRepository, liveStateRegistry)
        }

    @Test
    fun `given a failed flush when stopping then its deltas are sent with the unregistration`() = runTest {
        val step = step(backgroundScope)
        coEvery { countsRepository.increment(any(), any(), any(), any(), any()) } throws RuntimeException("Redis down")
        completeAs(50.0, last = false)
        step.start(context)

        step.execute(Entity("a", 2), Entity("a", 1), Entity("a", 3))
        advanceTimeBy(LINGER.toMillis() + 1)
        step.stop(context)

        coVerifyOrder {
            countsRepository.increment(CAMPAIGN, SCENARIO, STEP, 1, 1)
            countsRepository.unregister(CAMPAIGN, SCENARIO, STEP, 1, 1)
        }
    }

    @Test
    fun `given concurrent records of a same key when stopping then each record after the first is one transition`() =
        runTest {
            val step = step(backgroundScope)
            completeAs(100.0, last = false)
            val version = AtomicLong()
            step.start(context)

            withContext(Dispatchers.Default) {
                (1..8).map {
                    async { repeat(1_000) { step.execute(Entity("a", version.incrementAndGet())) } }
                }.awaitAll()
            }
            step.stop(context)

            val valid = slot<Long>()
            val invalid = slot<Long>()
            coVerify { countsRepository.unregister(CAMPAIGN, SCENARIO, STEP, capture(valid), capture(invalid)) }
            assertThat(valid.captured + invalid.captured).isEqualTo(7_999L)
        }

    @Test
    fun `given a stopped step when restarted then the previous values are forgotten`() = runTest {
        val step = step(backgroundScope)
        completeAs(null, last = true)

        step.start(context)
        step.execute(Entity("a", 1))
        step.stop(context)
        assertThat(step.getProperty<ConcurrentHashMap<Any, Any>>("lastValues")).isEmpty()
        step.start(context)
        step.execute(Entity("a", 0))
        step.stop(context)

        coVerify(exactly = 2) { countsRepository.unregister(CAMPAIGN, SCENARIO, STEP, 0, 0) }
    }

    private fun step(
        scope: CoroutineScope,
        direction: EvolutionDirection = EvolutionDirection.INCREASING,
    ) = EvolutionVerificationStep<Entity>(
        id = STEP,
        retryPolicy = null,
        direction = direction,
        keyExtractor = { _, input -> input.id },
        valueExtractor = { input -> input.version?.let { @Suppress("UNCHECKED_CAST") (it as Comparable<Any>) } },
        expectedPercentile = 99.0,
        countsRepository = countsRepository,
        campaignReportLiveStateRegistry = liveStateRegistry,
        coroutineScope = scope,
        linger = LINGER
    )

    private fun completeAs(percentile: Double?, last: Boolean) {
        coEvery {
            countsRepository.unregister(CAMPAIGN, SCENARIO, STEP, any(), any())
        } returns EvolutionCompletion(percentile, last)
    }

    /**
     * Executes the step with each record and verifies that it is forwarded unchanged.
     */
    private suspend fun EvolutionVerificationStep<Entity>.execute(vararg records: Entity) {
        records.forEach { record ->
            val stepContext = createStepContext<Entity, Entity>(record)
            execute(stepContext)
            assertThat(stepContext.consumeOutputValue()).isEqualTo(record)
        }
    }

    private data class Entity(val id: String?, val version: Long?)

    private companion object {

        const val CAMPAIGN = "my-campaign"

        const val SCENARIO = "my-scenario"

        const val STEP = "my-step"

        const val MESSAGE_ID = "my-message"

        val LINGER: Duration = Duration.ofSeconds(1)
    }
}
