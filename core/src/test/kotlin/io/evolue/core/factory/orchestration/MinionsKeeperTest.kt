package io.evolue.core.factory.orchestration

import io.evolue.api.context.CampaignId
import io.evolue.api.context.MinionId
import io.evolue.api.context.ScenarioId
import io.evolue.api.events.EventsLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.orchestration.Scenario
import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.core.cross.driving.feedback.FeedbackProducer
import io.evolue.test.coroutines.CleanCoroutines
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.time.EvolueTimeAssertions
import io.evolue.test.time.coMeasureTime
import io.evolue.test.utils.getProperty
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Eric Jessé
 */
@WithMockk
@CleanCoroutines
internal class MinionsKeeperTest {

    @RelaxedMockK
    private lateinit var scenariosKeeper: ScenariosKeeper

    @RelaxedMockK
    private lateinit var runner: Runner

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var feedbackProducer: FeedbackProducer

    @RelaxedMockK
    private lateinit var runningMinionsLatch: SuspendedCountLatch

    @BeforeEach
    internal fun setUp() {
        every { meterRegistry.gauge(any(), any(), any<AtomicInteger>()) } returnsArgument 2
        every { meterRegistry.timer(any(), *anyVararg()) } returns relaxedMockk()
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

        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventsLogger, meterRegistry, feedbackProducer)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosKeeper.getDag("my-scenario", "my-dag")
            meterRegistry.gauge("minion-executing-steps",
                    listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventsLogger.info("minion-created", tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventsLogger.trace("minion-maintenance-routine-started",
                    tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }
        confirmVerified(scenariosKeeper, runner, eventsLogger, meterRegistry)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions").containsKey("my-minion"))
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

        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventsLogger, meterRegistry, feedbackProducer)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosKeeper.getDag("my-scenario", "my-dag")
            meterRegistry.gauge("minion-executing-steps",
                    listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventsLogger.info("minion-created", tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventsLogger.trace("minion-maintenance-routine-started",
                    tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }

        confirmVerified(scenariosKeeper, runner, eventsLogger, meterRegistry)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(minionsKeeper.getProperty<Map<ScenarioId, List<MinionImpl>>>(
                "readySingletonsMinions")["my-scenario"]!!.contains(minionSlot.captured))
    }

    @Test
    internal fun shouldNotCreateSingletonWhenDagDoesNotExist() {
        // given
        every {
            scenariosKeeper.getDag("my-scenario", "my-dag")
        } returns null

        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventsLogger, meterRegistry, feedbackProducer)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        // Wait to be sure the runner is not called.
        Thread.sleep(30)

        // then
        verify { scenariosKeeper.getDag("my-scenario", "my-dag") }
        confirmVerified(scenariosKeeper, runner, eventsLogger, meterRegistry)
        Assertions.assertFalse(minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions").containsKey("my-minion"))
        Assertions.assertFalse(minionsKeeper.getProperty<Map<ScenarioId, List<MinionImpl>>>("readySingletonsMinions")
            .containsKey("my-scenario"))
    }

    @Test
    internal fun shouldStartScenarioAndSingletonsImmediately() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventsLogger, meterRegistry, feedbackProducer)
        val minion1: MinionImpl = relaxedMockk {
            every { campaignId } returns "my-campaign"
        }
        val minion2: MinionImpl = relaxedMockk {
            every { campaignId } returns "my-campaign"
        }
        coEvery { scenariosKeeper.hasScenario(eq("my-scenario")) } returns true
        minionsKeeper.getProperty<MutableMap<ScenarioId, List<MinionImpl>>>("readySingletonsMinions")["my-scenario"] =
            mutableListOf(minion1, minion2)
        minionsKeeper.getProperty<MutableMap<CampaignId, SuspendedCountLatch>>(
                "minionsCountLatchesByCampaign")["my-campaign"] = runningMinionsLatch

        // when
        runBlocking {
            minionsKeeper.startCampaign("my-campaign", "my-scenario")
        }

        coVerify {
            scenariosKeeper.hasScenario(eq("my-scenario"))
            scenariosKeeper.startScenario(eq("my-campaign"), eq("my-scenario"))
            minion1.start()
            minion2.start()
        }
        confirmVerified(scenariosKeeper, eventsLogger, meterRegistry)
    }

    @Test
    internal fun shouldIgnoreStartScenarioSingletonsStartWhenScenarioNotExist() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventsLogger, meterRegistry, feedbackProducer)
        val minion1: MinionImpl = mockk(relaxed = true)
        val minion2: MinionImpl = mockk(relaxed = true)
        minionsKeeper.getProperty<MutableMap<ScenarioId, List<MinionImpl>>>("readySingletonsMinions")["my-scenario"] =
            mutableListOf(minion1, minion2)

        // when
        runBlocking {
            minionsKeeper.startCampaign("my-campaign", "my-other-scenario")
        }

        coVerify {
            scenariosKeeper.hasScenario(eq("my-other-scenario"))
        }

        confirmVerified(minion1, minion2, scenariosKeeper, eventsLogger, meterRegistry)
    }

    @Test
    internal fun shouldStartMinionImmediately() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventsLogger, meterRegistry, feedbackProducer)
        val startTime = AtomicLong()
        val latch = SuspendedCountLatch(1)
        val minion: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
            every { campaignId } returns "my-campaign"
        }
        minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")["my-minion"] = minion
        minionsKeeper.getProperty<MutableMap<CampaignId, SuspendedCountLatch>>(
                "minionsCountLatchesByCampaign")["my-campaign"] = runningMinionsLatch

        // when
        val duration = coMeasureTime {
            minionsKeeper.startMinionAt("my-minion", Instant.now())
            latch.await()
        }

        // then
        coVerifyOnce {
            minion.start()
        }
        EvolueTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(2), duration)
        confirmVerified(eventsLogger, meterRegistry)
    }

    @Test
    internal fun shouldIgnoreScenarioStartWhenNotExist() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventsLogger, meterRegistry, feedbackProducer)
        val minion: MinionImpl = mockk(relaxed = true)
        minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")["my-minion"] = minion

        // when
        runBlocking {
            minionsKeeper.startCampaign("my-campaign", "my-other-scenario")
            delay(50)
        }

        coVerify {
            scenariosKeeper.hasScenario(eq("my-other-scenario"))
        }

        // then
        confirmVerified(minion, scenariosKeeper, eventsLogger, meterRegistry)
    }

    @Test
    internal fun shouldStartMinionLater() {
        // given
        val minionsKeeper = MinionsKeeper(scenariosKeeper, runner, eventsLogger, meterRegistry, feedbackProducer)
        val latch = SuspendedCountLatch(1)
        val minion: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                latch.release()
                logger().debug("Minion was started.")
            }
            every { campaignId } returns "my-campaign"
        }
        minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")["my-minion"] = minion
        minionsKeeper.getProperty<MutableMap<CampaignId, SuspendedCountLatch>>(
                "minionsCountLatchesByCampaign")["my-campaign"] = runningMinionsLatch

        // when
        val duration = coMeasureTime {
            minionsKeeper.startMinionAt("my-minion", Instant.now().plusMillis(50))
            latch.await()
        }

        // then
        coVerifyOnce { minion.start() }
        EvolueTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(50), duration)
        confirmVerified(eventsLogger, meterRegistry)
    }
}
