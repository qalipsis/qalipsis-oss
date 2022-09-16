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

package io.qalipsis.core.factory.orchestration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyExactly
import io.qalipsis.test.time.QalipsisTimeAssertions.assertLongerOrEqualTo
import io.qalipsis.test.time.coMeasureTime
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@WithMockk
internal class MinionImplTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var executingStepsGauge: AtomicInteger

    private val coroutinesExecutionTime = Duration.ofMillis(500)

    @BeforeAll
    internal fun setUpAll() {
        loggerContext.getLogger(MinionImpl::class.java).level =
            loggerContext.getLogger(MinionImpl::class.java.`package`.name).level
        loggerContext.getLogger(SuspendedCountLatch::class.java).level =
            loggerContext.getLogger(SuspendedCountLatch::class.java.`package`.name).level
    }

    @Test
    @Timeout(5)
    internal fun `attach and join`(): Unit = testCoroutineDispatcher.run {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", false, false, executingStepsGauge)
        minion.onComplete { completionCounter.incrementAndGet() }
        val executionCounter = AtomicInteger()

        // when
        val latch = Latch(true)
        repeat(5) { index ->
            minion.launch(this) {
                latch.await()
                // Add 5ms to thread preemption and rounding issues.
                delay(index * coroutinesExecutionTime.toMillis() + 5)
                executionCounter.incrementAndGet()
            }
        }
        latch.release()

        val executionDuration = coMeasureTime { minion.join() }

        // then
        assertLongerOrEqualTo(coroutinesExecutionTime.multipliedBy(4), executionDuration)
        assertEquals(5, executionCounter.get())
        assertEquals(1, completionCounter.get())

        verifyExactly(5) { executingStepsGauge.incrementAndGet() }
        verifyExactly(5) { executingStepsGauge.decrementAndGet() }

        confirmVerified(executingStepsGauge, executingStepsGauge)
    }

    @Test
    @Timeout(3)
    internal fun `should suspend caller until the minion starts`(): Unit = testCoroutineDispatcher.run {
        // given
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", true, false, executingStepsGauge)
        val latch = Latch(true)

        // when
        launch {
            latch.await()
            // Add 20ms to thread preemption and rounding issues.
            delay(coroutinesExecutionTime.toMillis() + 20)
            minion.start()
        }
        val executionDuration = coMeasureTime {
            latch.release()
            minion.waitForStart()
        }

        // then
        assertLongerOrEqualTo(coroutinesExecutionTime, executionDuration)

        confirmVerified(executingStepsGauge, executingStepsGauge)
    }

    @Test
    @Timeout(3)
    internal fun `wait for start and cancel`() = testCoroutineDispatcher.run {
        // given
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", true, false, executingStepsGauge)
        val latch = Latch(true)

        // when
        launch {
            latch.await()
            // Add 20ms to thread preemption and rounding issues.
            delay(coroutinesExecutionTime.toMillis() + 20)
            minion.cancel()
        }

        val executionDuration = coMeasureTime {
            latch.release()
            minion.waitForStart()
            delay(1)
        }

        // then
        assertLongerOrEqualTo(coroutinesExecutionTime, executionDuration)

        confirmVerified(executingStepsGauge, executingStepsGauge)
    }

    @Test
    @Timeout(1)
    internal fun `join and cancel`() = testCoroutineDispatcher.runTest {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", false, false, executingStepsGauge)
        minion.onComplete {
            completionCounter.incrementAndGet()
        }
        val executionCounter = AtomicInteger()
        val startLatch = Latch(true)

        // when attaching 3 suspended jobs to the minion
        launch {
            repeat(3) {
                minion.launch(this) {
                    startLatch.await() // This latch remains locked forever.
                    executionCounter.incrementAndGet()
                }
            }
        }

        // then
        val latch = Latch(true)
        launch {
            latch.await()
            minion.cancel()
        }
        latch.cancel()
        minion.join()

        assertEquals(0, executionCounter.get())
        assertEquals(0, completionCounter.get())

        verifyExactly(3) {
            executingStepsGauge.incrementAndGet()
            executingStepsGauge.decrementAndGet()
        }
    }

    @Test
    @Timeout(3)
    internal fun `should suspend join call when no job start`() = testCoroutineDispatcher.runTest {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", false, false, executingStepsGauge)
        minion.onComplete { completionCounter.incrementAndGet() }

        // then
        assertThrows<TimeoutCancellationException> {
            withTimeout(coroutinesExecutionTime.toMillis()) {
                minion.join()
            }
        }
        assertEquals(0, completionCounter.get())

        confirmVerified(executingStepsGauge)
    }

    @Test
    @Timeout(10)
    internal fun `should support a lot of minions with a lot of jobs`() = testCoroutineDispatcher.runTest {
        // given
        // Reduces the logs which affect the performances significantly.
        loggerContext.getLogger(MinionImpl::class.java).level = Level.INFO
        loggerContext.getLogger(SuspendedCountLatch::class.java).level = Level.INFO

        val minionsCount = 1000
        val stepsCount = 100

        val executionCounter = AtomicInteger()
        val completionCounter = AtomicInteger()

        val minions = mutableListOf<MinionImpl>()
        for (i in 0 until minionsCount) {
            val minion = MinionImpl("$i", "my-campaign", "my-scenario", false, false, AtomicInteger())
            minion.onComplete { completionCounter.incrementAndGet() }
            minions.add(minion)
        }

        `executes a lot of jobs on a lot of minions`(
            minionsCount,
            stepsCount,
            minions,
            executionCounter,
            completionCounter
        )
    }

    @Test
    @Timeout(10)
    internal fun `should be able to execute a workflow twice without pausing before restart`() =
        testCoroutineDispatcher.runTest {
            // given
            // Reduces the logs which affect the performances significantly.
            loggerContext.getLogger(MinionImpl::class.java).level = Level.INFO
            loggerContext.getLogger(SuspendedCountLatch::class.java).level = Level.INFO

            val minionsCount = 1000
            val stepsCount = 100

            val executionCounter = AtomicInteger()
            val completionCounter = AtomicInteger()

            val minions = mutableListOf<MinionImpl>()
            for (i in 0 until minionsCount) {
                val minion = MinionImpl("$i", "my-campaign", "my-scenario", false, false, AtomicInteger())
                minion.onComplete { completionCounter.incrementAndGet() }
                minions.add(minion)
            }

            `executes a lot of jobs on a lot of minions`(
                minionsCount,
                stepsCount,
                minions,
                executionCounter,
                completionCounter
            )

            executionCounter.set(0)
            completionCounter.set(0)
            minions.forEach {
                it.cancel()
                it.reset(false)
            }

            `executes a lot of jobs on a lot of minions`(
                minionsCount,
                stepsCount,
                minions,
                executionCounter,
                completionCounter
            )
        }

    @Test
    @Timeout(10)
    internal fun `should be able to execute a workflow twice with pausing before restart`() =
        testCoroutineDispatcher.runTest {
            // given
            // Reduces the logs which affect the performances significantly.
            loggerContext.getLogger(MinionImpl::class.java).level = Level.INFO
            loggerContext.getLogger(SuspendedCountLatch::class.java).level = Level.INFO

            val minionsCount = 1000
            val stepsCount = 100

            val executionCounter = AtomicInteger()
            val completionCounter = AtomicInteger()

            val minions = mutableListOf<MinionImpl>()
            for (i in 0 until minionsCount) {
                val minion = MinionImpl("$i", "my-campaign", "my-scenario", false, false, AtomicInteger())
                minion.onComplete { completionCounter.incrementAndGet() }
                minions.add(minion)
            }

            `executes a lot of jobs on a lot of minions`(
                minionsCount,
                stepsCount,
                minions,
                executionCounter,
                completionCounter
            )

            executionCounter.set(0)
            completionCounter.set(0)
            minions.forEach {
                it.cancel()
                it.reset(true)
                it.start()
            }

            `executes a lot of jobs on a lot of minions`(
                minionsCount,
                stepsCount,
                minions,
                executionCounter,
                completionCounter
            )
        }

    private suspend fun `executes a lot of jobs on a lot of minions`(
        minionsCount: Int,
        stepsCount: Int,
        minions: MutableList<MinionImpl>,
        executionCounter: AtomicInteger,
        completionCounter: AtomicInteger
    ) {
        coroutineScope {
            val startLatch = SuspendedCountLatch(1)
            val stepsCountDown = SuspendedCountLatch(minionsCount.toLong() * stepsCount)
            launch {
                minions.forEach { minion ->
                    repeat(stepsCount) {
                        minion.launch(this) {
                            startLatch.await()
                            executionCounter.incrementAndGet()
                        }
                        stepsCountDown.decrement()
                    }
                }
            }
            stepsCountDown.await()
            log.debug { "All ${minionsCount * stepsCount} steps were created" }
            minions.forEach {
                assertEquals(
                    stepsCount, it.stepsCount,
                    "Minion ${it.id} has ${it.stepsCount} steps but $stepsCount are expected"
                )
            }
            startLatch.release()
            log.debug { "Joining all minions" }
            minions.forEach { it.join() }
            assertEquals(minionsCount, completionCounter.get())
            assertEquals(minionsCount * stepsCount, executionCounter.get())
        }
    }

    companion object {

        @JvmStatic
        private val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

        @JvmStatic
        private val log = logger()
    }
}
