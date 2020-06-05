package io.evolue.core.factory.orchestration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import io.evolue.api.events.EventLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyExactly
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.time.EvolueTimeAssertions.assertLongerOrEqualTo
import io.evolue.test.time.coMeasureTime
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
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
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class MinionImplTest {

    @RelaxedMockK
    lateinit var eventLogger: EventLogger

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    lateinit var executingStepsGauge: AtomicInteger

    private val coroutinesExecutionTime = Duration.ofMillis(200)

    private var initialized = false

    @BeforeEach
    internal fun setUp() {
        loggerContext.getLogger(MinionImpl::class.java).level =
            loggerContext.getLogger(MinionImpl::class.java.`package`.name).level
        loggerContext.getLogger(SuspendedCountLatch::class.java).level =
            loggerContext.getLogger(SuspendedCountLatch::class.java.`package`.name).level

        if (!initialized) {
            every {
                meterRegistry.gauge("minion-executing-steps", any(), any<AtomicInteger>())
            } returns executingStepsGauge
            every { meterRegistry.timer("minion-maintenance", "minion", any()) } returns relaxedMockk()
            initialized = true
        }
    }

    @Test
    @Timeout(1)
    internal fun attachAndJoin() {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-campaign", "my-minion", false, eventLogger, meterRegistry)
        minion.onComplete { completionCounter.incrementAndGet() }
        val executionCounter = AtomicInteger()

        // when
        GlobalScope.launch {
            repeat(3) {
                minion.attach(GlobalScope.launch {
                    delay(coroutinesExecutionTime.toMillis())
                    executionCounter.incrementAndGet()
                })
            }
        }

        // then
        val executionDuration = coMeasureTime {
            minion.join()
        }
        assertLongerOrEqualTo(coroutinesExecutionTime, executionDuration)
        assertEquals(3, executionCounter.get())
        assertEquals(1, completionCounter.get())

        verifyOnce {
            meterRegistry.gauge("minion-executing-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventLogger.debug("minion-created", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-started", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-maintenance-routine-started", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-completed", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        verifyExactly(3) { executingStepsGauge.incrementAndGet() }
        verifyExactly(3) { executingStepsGauge.decrementAndGet() }

        confirmVerified(eventLogger, meterRegistry, executingStepsGauge)
    }

    @Test
    @Timeout(1)
    internal fun shouldSuspendCallerUntilTheMinionStarts() {
        // given
        val minion = MinionImpl("my-campaign", "my-minion", true, eventLogger, meterRegistry)

        // when
        GlobalScope.launch {
            delay(coroutinesExecutionTime.toMillis() + 5)
            minion.start()
        }
        val executionDuration = coMeasureTime {
            minion.waitForStart()
        }

        // then
        assertLongerOrEqualTo(coroutinesExecutionTime, executionDuration)

        verifyOnce {
            meterRegistry.gauge("minion-executing-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventLogger.debug("minion-created", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-started", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-maintenance-routine-started", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        confirmVerified(eventLogger, meterRegistry, executingStepsGauge)
    }

    @Test
    @Timeout(1)
    internal fun waitForStartAndCancel() {
        // given
        val minion = MinionImpl("my-campaign", "my-minion", true, eventLogger, meterRegistry)

        // when
        GlobalScope.launch {
            // Add 1ms to avoid rounding issues.
            delay(coroutinesExecutionTime.toMillis() + 1)
            minion.cancel()
        }
        val executionDuration = coMeasureTime {
            minion.waitForStart()
            delay(1)
        }

        // then
        assertLongerOrEqualTo(coroutinesExecutionTime, executionDuration)
        verifyOnce {
            meterRegistry.gauge("minion-executing-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventLogger.debug("minion-created", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-maintenance-routine-started", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-cancellation-started", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-cancellation-completed", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }

        confirmVerified(eventLogger, meterRegistry, executingStepsGauge)
    }

    @Test
    @Timeout(1)
    internal fun joinAndCancel() {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-campaign", "my-minion", false, eventLogger, meterRegistry)
        minion.onComplete { completionCounter.incrementAndGet() }
        val executionCounter = AtomicInteger()
        val startLatch = SuspendedCountLatch(1)

        // when
        GlobalScope.launch {
            repeat(3) {
                minion.attach(GlobalScope.launch {
                    startLatch.await()
                    executionCounter.incrementAndGet()
                })
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
            meterRegistry.gauge("minion-executing-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventLogger.debug("minion-created", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-started", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-maintenance-routine-started", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-cancellation-started", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-cancellation-completed", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        verifyExactly(3) {
            executingStepsGauge.incrementAndGet()
            executingStepsGauge.decrementAndGet()
        }

        confirmVerified(eventLogger, meterRegistry, executingStepsGauge)
    }

    @Test
    @Timeout(1)
    internal fun shouldSuspendJoinCallWhenNoJobStart() {
        // given
        val completionCounter = AtomicInteger()
        val minion = MinionImpl("my-campaign", "my-minion", false, eventLogger, meterRegistry)
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
            meterRegistry.gauge("minion-executing-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventLogger.debug("minion-created", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-started", null, mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-maintenance-routine-started", null,
                mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }

        confirmVerified(eventLogger, meterRegistry, executingStepsGauge)
    }

    @Test
    @Timeout(8)
    internal fun shouldSupportALotOfMinionsWithALotOfJobs() {
        // given
        // Reduces the logs which affect the performances significantly.
        loggerContext.getLogger(MinionImpl::class.java).level = Level.INFO
        loggerContext.getLogger(SuspendedCountLatch::class.java).level = Level.INFO
        every { meterRegistry.gauge("minion-executing-steps", any(), any<AtomicInteger>()) } returnsArgument 2

        val minionsCount = 1000
        val stepsCount = 100

        val executionCounter = AtomicInteger()
        val completionCounter = AtomicInteger()

        val minions = mutableListOf<MinionImpl>()
        for (i in 0 until minionsCount) {
            val minion = MinionImpl("my-campaign", "$i", false, eventLogger, meterRegistry)
            minion.onComplete { completionCounter.incrementAndGet() }
            minions.add(minion)
        }

        val startLatch = SuspendedCountLatch(1)
        val stepsCountDown = CountDownLatch(minionsCount * stepsCount)

        // when
        GlobalScope.launch {
            minions.forEach { minion ->
                repeat(stepsCount) {
                    minion.attach(launch {
                        startLatch.await()
                        executionCounter.incrementAndGet()
                    })
                    stepsCountDown.countDown()
                }
            }
        }

        stepsCountDown.await()
        log.debug("All ${minionsCount * stepsCount} steps were created")

        minions.forEach {
            assertEquals(stepsCount, it.stepsCount,
                "Minion ${it.id} has ${it.stepsCount} steps but $stepsCount are expected")
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