package io.evolue.core.factory.orchestration

import io.evolue.api.events.EventLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.orchestration.Scenario
import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.test.coroutines.AbstractCoroutinesTest
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.time.EvolueTimeAssertions
import io.evolue.test.time.coMeasureTime
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class MinionsKeeperTest : AbstractCoroutinesTest() {

    @RelaxedMockK
    lateinit private var scenariosKeeper: ScenariosKeeper

    @RelaxedMockK
    lateinit private var runner: Runner

    @RelaxedMockK
    lateinit private var eventLogger: EventLogger

    @RelaxedMockK
    lateinit private var meterRegistry: MeterRegistry

    private var initialized = false

    @BeforeEach
    internal fun setUp() {
        if (initialized) {
            return
        }

        every { meterRegistry.gauge(any(), any(), any<AtomicInteger>()) } returnsArgument 2
        every { meterRegistry.timer(any(), *anyVararg()) } returns relaxedMockk()
        initialized = true
    }

    @Test
    @Timeout(1)
    internal fun shouldCreatePausedMinion() {
        // given
        val dag = DirectedAcyclicGraph("my-dag", Scenario("my-scenario", rampUpStrategy = relaxedMockk()),
            singleton = false, scenarioStart = true)
        every {
            scenariosKeeper.getDag("my-scenario", "my-dag")
        } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = CountDownLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } answers { runnerCountDown.countDown() }

        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventLogger, meterRegistry)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosKeeper.getDag("my-scenario", "my-dag")
            meterRegistry.gauge("minion-executing-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventLogger.debug("minion-created", tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-maintenance-routine-started",
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }
        confirmVerified(scenariosKeeper, runner, eventLogger, meterRegistry)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(minionsKeeper.minions.containsKey("my-minion"))
    }

    @Test
    internal fun shouldCreateSingletonPausedMinion() {
        // given
        val dag = DirectedAcyclicGraph("my-dag", Scenario("my-scenario", rampUpStrategy = relaxedMockk()),
            singleton = true, scenarioStart = true)
        every {
            scenariosKeeper.getDag("my-scenario", "my-dag")
        } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = CountDownLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } answers { runnerCountDown.countDown() }

        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventLogger, meterRegistry)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosKeeper.getDag("my-scenario", "my-dag")
            meterRegistry.gauge("minion-executing-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventLogger.debug("minion-created", tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventLogger.debug("minion-maintenance-routine-started",
                tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }

        confirmVerified(scenariosKeeper, runner, eventLogger, meterRegistry)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(minionsKeeper.readySingletonsMinions["my-scenario"]!!.contains(minionSlot.captured))
    }

    @Test
    internal fun shouldNotCreateSingletonWhenDagDoesNotExist() {
        // given
        every {
            scenariosKeeper.getDag("my-scenario", "my-dag")
        } returns null

        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventLogger, meterRegistry)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        // Wait to be sure the runner is not called.
        Thread.sleep(30)

        // then
        verify { scenariosKeeper.getDag("my-scenario", "my-dag") }
        confirmVerified(scenariosKeeper, runner, eventLogger, meterRegistry)
        Assertions.assertFalse(minionsKeeper.minions.containsKey("my-minion"))
        Assertions.assertFalse(minionsKeeper.readySingletonsMinions.containsKey("my-scenario"))
    }

    @Test
    internal fun shouldStartSingletonsImmediately() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventLogger, meterRegistry)
        val minion1: MinionImpl = mockk(relaxed = true)
        val minion2: MinionImpl = mockk(relaxed = true)
        minionsKeeper.readySingletonsMinions["my-scenario"] = mutableListOf(minion1, minion2)

        // when
        runBlocking {
            minionsKeeper.startSingletons("my-scenario")
        }

        coVerify {
            minion1.start()
            minion2.start()
        }
        confirmVerified(eventLogger, meterRegistry)
    }

    @Test
    internal fun shouldIgnoreSingletonsStartWhenScenarioNotExist() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventLogger, meterRegistry)
        val minion1: MinionImpl = mockk(relaxed = true)
        val minion2: MinionImpl = mockk(relaxed = true)
        minionsKeeper.readySingletonsMinions["my-scenario"] = mutableListOf(minion1, minion2)

        // when
        runBlocking {
            minionsKeeper.startSingletons("my-other-scenario")
        }

        confirmVerified(minion1, minion2, eventLogger, meterRegistry)
    }

    @Test
    internal fun shouldStartMinionImmediately() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventLogger, meterRegistry)
        val startTime = AtomicLong()
        val latch = SuspendedCountLatch(1)
        val minion: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
        }
        minionsKeeper.minions["my-minion"] = minion

        // when
        val duration = coMeasureTime {
            minionsKeeper.startMinionAt("my-minion", Instant.now())
            latch.await()
        }

        // then
        coVerifyOnce { minion.start() }
        EvolueTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(2), duration)
        confirmVerified(eventLogger, meterRegistry)
    }

    @Test
    internal fun shouldIgnoreMinionStartWhenNotExist() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventLogger, meterRegistry)
        val minion: MinionImpl = mockk(relaxed = true)
        minionsKeeper.minions["my-minion"] = minion

        // when
        runBlocking {
            minionsKeeper.startSingletons("my-other-scenario")
            delay(50)
        }

        // then
        confirmVerified(minion, eventLogger, meterRegistry)
    }

    @Test
    internal fun shouldStartMinionLater() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventLogger, meterRegistry)
        val latch = SuspendedCountLatch(1)
        val minion: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                latch.release()
                logger().debug("Minion was started.")
            }
        }
        minionsKeeper.minions["my-minion"] = minion

        // when
        val duration = coMeasureTime {
            minionsKeeper.startMinionAt("my-minion", Instant.now().plusMillis(50))
            latch.await()
        }

        // then
        coVerifyOnce { minion.start() }
        EvolueTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(50), duration)
        confirmVerified(eventLogger, meterRegistry)
    }
}