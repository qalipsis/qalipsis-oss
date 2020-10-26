package io.qalipsis.core.factories.orchestration

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
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factories.testDag
import io.qalipsis.test.coroutines.CleanCoroutines
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.time.QalipsisTimeAssertions
import io.qalipsis.test.time.coMeasureTime
import io.qalipsis.test.utils.getProperty
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
internal class MinionsKeeperImplTest {

    @RelaxedMockK
    private lateinit var scenariosRegistry: ScenariosRegistry

    @RelaxedMockK
    private lateinit var runner: RunnerImpl

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
        val dag = testDag(isUnderLoad = true)
        every {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
        } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = CountDownLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } answers { runnerCountDown.countDown() }

        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
            meterRegistry.gauge("minion-executing-steps",
                    listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventsLogger.info("minion-created", tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventsLogger.trace("minion-maintenance-routine-started",
                    tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }
        confirmVerified(scenariosRegistry, runner, eventsLogger, meterRegistry)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions").containsKey("my-minion"))
    }

    @Test
    internal fun shouldCreateSingletonPausedMinion() {
        // given
        val dag = testDag(isSingleton = true, isUnderLoad = true)
        every {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
        } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = CountDownLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } answers { runnerCountDown.countDown() }

        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
            meterRegistry.gauge("minion-executing-steps",
                    listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventsLogger.info("minion-created", tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventsLogger.trace("minion-maintenance-routine-started",
                    tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }

        confirmVerified(scenariosRegistry, runner, eventsLogger, meterRegistry)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(minionsKeeper.getProperty<Map<ScenarioId, Collection<MinionImpl>>>(
                "readySingletonsMinions")["my-scenario"]!!.contains(minionSlot.captured))
    }


    @Test
    internal fun shouldCreateNonSingletonNotUnderLoadPausedMinion() {
        // given
        val dag = testDag(isUnderLoad = false, root = true)
        every {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
        } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = CountDownLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } answers { runnerCountDown.countDown() }

        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
            meterRegistry.gauge("minion-executing-steps",
                    listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>())
            meterRegistry.timer("minion-maintenance", "campaign", "my-campaign", "minion", "my-minion")
            eventsLogger.info("minion-created", tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
            eventsLogger.trace("minion-maintenance-routine-started",
                    tags = mapOf("campaign" to "my-campaign", "minion" to "my-minion"))
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }

        confirmVerified(scenariosRegistry, runner, eventsLogger, meterRegistry)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(minionsKeeper.getProperty<Map<ScenarioId, Collection<MinionImpl>>>(
                "readySingletonsMinions")["my-scenario"]!!.contains(minionSlot.captured))
    }

    @Test
    internal fun shouldNotCreateSingletonWhenDagDoesNotExist() {
        // given
        every {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
        } returns null

        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        // Wait to be sure the runner is not called.
        Thread.sleep(30)

        // then
        verify { scenariosRegistry.get("my-scenario")?.get("my-dag") }
        confirmVerified(scenariosRegistry, runner, eventsLogger, meterRegistry)
        Assertions.assertFalse(minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions").containsKey("my-minion"))
        Assertions.assertFalse(minionsKeeper.getProperty<Map<ScenarioId, List<MinionImpl>>>("readySingletonsMinions")
            .containsKey("my-scenario"))
    }

    @Test
    internal fun shouldStartScenarioAndSingletonsImmediately() {
        // given
        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)
        val minion1: MinionImpl = relaxedMockk {
            every { campaignId } returns "my-campaign"
        }
        val minion2: MinionImpl = relaxedMockk {
            every { campaignId } returns "my-campaign"
        }
        val scenario = relaxedMockk<Scenario>()
        coEvery { scenariosRegistry.contains(eq("my-scenario")) } returns true
        coEvery { scenariosRegistry.get(eq("my-scenario")) } returns scenario
        minionsKeeper.getProperty<MutableMap<ScenarioId, List<MinionImpl>>>("readySingletonsMinions")["my-scenario"] =
            mutableListOf(minion1, minion2)
        minionsKeeper.getProperty<MutableMap<CampaignId, SuspendedCountLatch>>(
                "minionsCountLatchesByCampaign")["my-campaign"] = runningMinionsLatch

        // when
        runBlocking {
            minionsKeeper.startCampaign("my-campaign", "my-scenario")
        }

        coVerify {
            scenariosRegistry.contains(eq("my-scenario"))
            scenariosRegistry.get(eq("my-scenario"))
            scenario.start(eq("my-campaign"))
            minion1.start()
            minion2.start()
        }
        confirmVerified(scenariosRegistry, eventsLogger, meterRegistry)
    }

    @Test
    internal fun shouldIgnoreStartScenarioSingletonsStartWhenScenarioNotExist() {
        // given
        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)
        val minion1: MinionImpl = mockk(relaxed = true)
        val minion2: MinionImpl = mockk(relaxed = true)
        minionsKeeper.getProperty<MutableMap<ScenarioId, List<MinionImpl>>>("readySingletonsMinions")["my-scenario"] =
            mutableListOf(minion1, minion2)

        // when
        runBlocking {
            minionsKeeper.startCampaign("my-campaign", "my-other-scenario")
        }

        coVerify {
            scenariosRegistry.contains(eq("my-other-scenario"))
        }

        confirmVerified(minion1, minion2, scenariosRegistry, eventsLogger, meterRegistry)
    }

    @Test
    internal fun shouldStartMinionImmediately() {
        // given
        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)
        val startTime = AtomicLong()
        val latch = SuspendedCountLatch(1)
        val minion1: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
            every { campaignId } returns "my-campaign"
        }
        val minion2: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
            every { campaignId } returns "my-campaign"
        }
        minionsKeeper.getProperty<MutableMap<MinionId, MutableList<MinionImpl>>>("minions")["my-minion"] =
            mutableListOf(minion1, minion2)
        minionsKeeper.getProperty<MutableMap<CampaignId, SuspendedCountLatch>>(
                "minionsCountLatchesByCampaign")["my-campaign"] = runningMinionsLatch

        // when
        val duration = coMeasureTime {
            minionsKeeper.startMinionAt("my-minion", Instant.now())
            latch.await()
        }

        // then
        coVerifyOnce {
            minion1.start()
            minion2.start()
        }
        QalipsisTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(10), duration)
        confirmVerified(eventsLogger, meterRegistry)
    }

    @Test
    internal fun shouldIgnoreScenarioStartWhenNotExist() {
        // given
        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)
        val minion: MinionImpl = mockk(relaxed = true)
        minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")["my-minion"] = minion

        // when
        runBlocking {
            minionsKeeper.startCampaign("my-campaign", "my-other-scenario")
            delay(50)
        }

        coVerify {
            scenariosRegistry.contains(eq("my-other-scenario"))
        }

        // then
        confirmVerified(minion, scenariosRegistry, eventsLogger, meterRegistry)
    }

    @Test
    internal fun shouldStartMinionLater() {
        // given
        val minionsKeeper =
            MinionsKeeperImpl(scenariosRegistry, runner, eventsLogger, meterRegistry, feedbackProducer)
        val latch = SuspendedCountLatch(2)
        val minion1: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                latch.release()
                logger().debug("Minion 1 was started.")
            }
            every { campaignId } returns "my-campaign"
        }
        val minion2: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                latch.release()
                logger().debug("Minion 2 was started.")
            }
            every { campaignId } returns "my-campaign"
        }
        minionsKeeper.getProperty<MutableMap<MinionId, MutableList<MinionImpl>>>("minions")["my-minion"] =
            mutableListOf(minion1, minion2)
        minionsKeeper.getProperty<MutableMap<CampaignId, SuspendedCountLatch>>(
                "minionsCountLatchesByCampaign")["my-campaign"] = runningMinionsLatch

        // when
        val duration = coMeasureTime {
            minionsKeeper.startMinionAt("my-minion", Instant.now().plusMillis(100))
            latch.await()
        }

        // then
        coVerifyOnce {
            minion1.start()
            minion2.start()
        }
        QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(100), duration)
        confirmVerified(eventsLogger, meterRegistry)
    }
}