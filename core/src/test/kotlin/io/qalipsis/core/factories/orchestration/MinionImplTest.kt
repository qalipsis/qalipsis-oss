package io.qalipsis.core.factories.orchestration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyExactly
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.time.QalipsisTimeAssertions.assertLongerOrEqualTo
import io.qalipsis.test.time.coMeasureTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@WithMockk
internal class MinionImplTest {

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    lateinit var executingStepsGauge: AtomicInteger

    private val coroutinesExecutionTime = Duration.ofMillis(200)

    @BeforeEach
    internal fun setUp() {
        loggerContext.getLogger(MinionImpl::class.java).level =
            loggerContext.getLogger(MinionImpl::class.java.`package`.name).level
        loggerContext.getLogger(SuspendedCountLatch::class.java).level =
            loggerContext.getLogger(SuspendedCountLatch::class.java.`package`.name).level

        every {
            meterRegistry.gauge("minion-running-steps", any(), any<AtomicInteger>())
        } returns executingStepsGauge
    }

    @Test
    @Timeout(5)
    internal fun attachAndJoin() {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)
        minion.onComplete { completionCounter.incrementAndGet() }
        val executionCounter = AtomicInteger()

        // when
        GlobalScope.launch {
            val latch = Latch(true)
            repeat(3) {
                minion.launch {
                    latch.await()
                    // Add 5ms to thread preemption and rounding issues.
                    delay(coroutinesExecutionTime.toMillis() + 5)
                    executionCounter.incrementAndGet()
                }
            }
            latch.release()
        }

        // then
        val executionDuration = coMeasureTime { minion.join() }
        assertLongerOrEqualTo(coroutinesExecutionTime, executionDuration)
        assertEquals(3, executionCounter.get())
        assertEquals(1, completionCounter.get())

        verifyOnce {
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.running",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.execution-complete", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
        }
        verifyExactly(3) { executingStepsGauge.incrementAndGet() }
        verifyExactly(3) { executingStepsGauge.decrementAndGet() }

        confirmVerified(eventsLogger, meterRegistry, executingStepsGauge)
    }

    @Test
    @Timeout(3)
    internal fun shouldSuspendCallerUntilTheMinionStarts() {
        // given
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", true, eventsLogger, meterRegistry)
        val latch = Latch(true)

        // when
        GlobalScope.launch {
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

        verifyOnce {
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.running",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
        }
        confirmVerified(eventsLogger, meterRegistry, executingStepsGauge)
    }

    @Test
    @Timeout(3)
    internal fun waitForStartAndCancel() {
        // given
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", true, eventsLogger, meterRegistry)
        val latch = Latch(true)

        // when
        GlobalScope.launch {
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
        verifyOnce {
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.cancellation.started", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.cancellation.complete", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
        }

        confirmVerified(eventsLogger, meterRegistry, executingStepsGauge)
    }

    @Test
    @Timeout(3)
    internal fun joinAndCancel() {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)
        minion.onComplete { completionCounter.incrementAndGet() }
        val executionCounter = AtomicInteger()
        val startLatch = Latch(true)

        // when attaching 3 suspended jobs to the minion
        GlobalScope.launch {
            repeat(3) {
                minion.launch {
                    startLatch.await()
                    executionCounter.incrementAndGet()
                }
            }
        }

        // then
        runBlocking {
            launch {
                // Just wait that the join was executed.
                delay(10)
                minion.cancel()
            }
            minion.join()
            delay(20)
        }
        assertEquals(0, executionCounter.get())
        assertEquals(0, completionCounter.get())

        verifyOnce {
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.running",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.cancellation.started", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.cancellation.complete", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
        }

        confirmVerified(eventsLogger, meterRegistry)
    }

    @Test
    @Timeout(3)
    internal fun shouldSuspendJoinCallWhenNoJobStart() {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)
        minion.onComplete { completionCounter.incrementAndGet() }

        // then
        assertThrows<TimeoutCancellationException> {
            runBlocking {
                withTimeout(coroutinesExecutionTime.toMillis()) {
                    minion.join()
                }
            }
        }
        assertEquals(0, completionCounter.get())

        verifyOnce {
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
            eventsLogger.info(
                "minion.running",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion")
            )
        }

        confirmVerified(eventsLogger, meterRegistry)
    }

    @Test
    @Timeout(20)
    internal fun shouldSupportALotOfMinionsWithALotOfJobs() {
        // given
        // Reduces the logs which affect the performances significantly.
        loggerContext.getLogger(MinionImpl::class.java).level = Level.INFO
        loggerContext.getLogger(SuspendedCountLatch::class.java).level = Level.INFO
        every { meterRegistry.gauge("minion-running-steps", any(), any<AtomicInteger>()) } returnsArgument 2

        val minionsCount = 1000
        val stepsCount = 100

        val executionCounter = AtomicInteger()
        val completionCounter = AtomicInteger()

        val minions = mutableListOf<MinionImpl>()
        for (i in 0 until minionsCount) {
            val minion = MinionImpl("$i", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)
            minion.onComplete { completionCounter.incrementAndGet() }
            minions.add(minion)
        }

        val startLatch = SuspendedCountLatch(1)
        val stepsCountDown = CountDownLatch(minionsCount * stepsCount)

        // when
        GlobalScope.launch {
            minions.forEach { minion ->
                repeat(stepsCount) {
                    minion.launch {
                        startLatch.await()
                        executionCounter.incrementAndGet()
                    }
                    stepsCountDown.countDown()
                }
            }
        }

        stepsCountDown.await()
        log.debug("All ${minionsCount * stepsCount} steps were created")

        minions.forEach {
            assertEquals(
                stepsCount, it.stepsCount,
                "Minion ${it.id} has ${it.stepsCount} steps but $stepsCount are expected"
            )
        }

        // then
        runBlocking {
            // Triggers the start for all the jobs at the same time.
            startLatch.release()
            log.debug("Joining all minions")
            minions.forEach { it.join() }
        }
        assertEquals(minionsCount, completionCounter.get())
        assertEquals(minionsCount * stepsCount, executionCounter.get())
    }

    companion object {

        @JvmStatic
        private val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

        @JvmStatic
        private val log = logger()
    }
}
