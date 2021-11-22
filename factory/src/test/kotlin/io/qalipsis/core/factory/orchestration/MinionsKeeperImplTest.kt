package io.qalipsis.core.factory.orchestration

import io.aerisconsulting.catadioptre.getProperty
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
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factory.testDag
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.time.QalipsisTimeAssertions
import io.qalipsis.test.time.coMeasureTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Eric Jessé
 */
@WithMockk
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
    private lateinit var campaignStateKeeper: CampaignStateKeeper

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var runningMinionsLatch: SuspendedCountLatch

    @BeforeEach
    internal fun setUp() {
        every { meterRegistry.gauge(any(), any(), any<AtomicInteger>()) } returnsArgument 2
        every { meterRegistry.timer(any(), *anyVararg()) } returns relaxedMockk()
    }

    @Test
    @Timeout(10)
    internal fun shouldCreatePausedMinion() = runBlockingTest {
        // given
        val dag = testDag(isUnderLoad = true)
        every {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
        } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = SuspendedCountLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } coAnswers { runnerCountDown.decrement() }

        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            this
        )

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
        }
        coVerifyOnce {
            runner.run(any(), refEq(dag))
        }
        confirmVerified(scenariosRegistry, runner, eventsLogger, meterRegistry, campaignStateKeeper)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions").containsKey("my-minion"))
    }

    @Test
    @Timeout(10)
    internal fun shouldCreateSingletonPausedMinion() = runBlockingTest {
        // given
        val dag = testDag(isSingleton = true, isUnderLoad = true)
        every {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
        } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = SuspendedCountLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } coAnswers { runnerCountDown.decrement() }

        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            this
        )

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }

        confirmVerified(scenariosRegistry, runner, eventsLogger, meterRegistry, campaignStateKeeper)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(
            minionsKeeper.getProperty<Map<ScenarioId, Collection<MinionImpl>>>(
                "readySingletonsMinions"
            )["my-scenario"]!!.contains(minionSlot.captured)
        )
    }


    @Test
    @Timeout(10)
    internal fun shouldCreateNonSingletonNotUnderLoadPausedMinion() = runBlockingTest {
        // given
        val dag = testDag(isUnderLoad = false, root = true)
        every {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
        } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = SuspendedCountLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } coAnswers { runnerCountDown.decrement() }

        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            this
        )

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(Tag.of("campaign", "my-campaign"), Tag.of("minion", "my-minion")), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
        }
        coVerifyOnce { runner.run(any(), refEq(dag)) }

        confirmVerified(scenariosRegistry, runner, eventsLogger, meterRegistry, campaignStateKeeper)

        Assertions.assertFalse(minionSlot.captured.isStarted())
        Assertions.assertTrue(
            minionsKeeper.getProperty<Map<ScenarioId, Collection<MinionImpl>>>(
                "readySingletonsMinions"
            )["my-scenario"]!!.contains(minionSlot.captured)
        )
    }

    @Test
    @Timeout(10)
    internal fun shouldNotCreateSingletonWhenDagDoesNotExist() = runBlockingTest {
        // given
        every {
            scenariosRegistry.get("my-scenario")?.get("my-dag")
        } returns null

        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            this
        )

        // when
        minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        // Wait to be sure the runner is not called.
        Thread.sleep(30)

        // then
        verify { scenariosRegistry.get("my-scenario")?.get("my-dag") }
        confirmVerified(scenariosRegistry, runner, eventsLogger, meterRegistry, campaignStateKeeper)
        Assertions.assertFalse(minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions").containsKey("my-minion"))
        Assertions.assertFalse(
            minionsKeeper.getProperty<Map<ScenarioId, List<MinionImpl>>>("readySingletonsMinions")
                .containsKey("my-scenario")
        )
    }

    @Test
    @Timeout(10)
    internal fun shouldStartScenarioAndSingletonsImmediately() = runBlockingTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            this
        )
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
            "minionsCountLatchesByCampaign"
        )["my-campaign"] = runningMinionsLatch

        // when
        minionsKeeper.startCampaign("my-campaign", "my-scenario")

        coVerify {
            scenariosRegistry.contains(eq("my-scenario"))
            scenariosRegistry.get(eq("my-scenario"))
            scenario.start(eq("my-campaign"))
            minion1.start()
            minion2.start()
            campaignStateKeeper.start(eq("my-campaign"), eq("my-scenario"))
        }
        confirmVerified(scenariosRegistry, eventsLogger, meterRegistry, campaignStateKeeper)
    }

    @Test
    @Timeout(10)
    internal fun shouldIgnoreStartScenarioSingletonsStartWhenScenarioNotExist() = runBlockingTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            this
        )
        val minion1: MinionImpl = mockk(relaxed = true)
        val minion2: MinionImpl = mockk(relaxed = true)
        minionsKeeper.getProperty<MutableMap<ScenarioId, List<MinionImpl>>>("readySingletonsMinions")["my-scenario"] =
            mutableListOf(minion1, minion2)

        // when
        minionsKeeper.startCampaign("my-campaign", "my-other-scenario")

        coVerify {
            scenariosRegistry.contains(eq("my-other-scenario"))
        }

        confirmVerified(minion1, minion2, scenariosRegistry, eventsLogger, meterRegistry, campaignStateKeeper)
    }

    @Test
    @Timeout(10)
    internal fun shouldStartMinionImmediately() {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            relaxedMockk()
        )
        val startTime = AtomicLong()
        val latch = SuspendedCountLatch(1)
        val minion1: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        val minion2: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        minionsKeeper.getProperty<MutableMap<MinionId, MutableList<MinionImpl>>>("minions")["my-minion"] =
            mutableListOf(minion1, minion2)
        minionsKeeper.getProperty<MutableMap<CampaignId, SuspendedCountLatch>>(
            "minionsCountLatchesByCampaign"
        )["my-campaign"] = runningMinionsLatch

        // when
        val duration = coMeasureTime {
            minionsKeeper.startMinionAt("my-minion", Instant.now())
            latch.await()
        }

        // then
        coVerifyOnce {
            minion1.start()
            minion2.start()
            campaignStateKeeper.recordStartedMinion(eq("my-campaign"), eq("my-scenario"), eq(2))
        }
        QalipsisTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(20), duration)
        confirmVerified(eventsLogger, meterRegistry, campaignStateKeeper)
    }

    @Test
    @Timeout(10)
    internal fun shouldIgnoreScenarioStartWhenNotExist() = runBlockingTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            this
        )
        val minion: MinionImpl = mockk(relaxed = true)
        minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")["my-minion"] = minion

        // when
        minionsKeeper.startCampaign("my-campaign", "my-other-scenario")
        delay(50)

        coVerify {
            scenariosRegistry.contains(eq("my-other-scenario"))
        }

        // then
        confirmVerified(minion, scenariosRegistry, eventsLogger, meterRegistry, campaignStateKeeper)
    }

    @Test
    @Timeout(10)
    internal fun shouldStartMinionLater() {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenariosRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            campaignStateKeeper,
            feedbackFactoryChannel,
            relaxedMockk()
        )
        val latch = SuspendedCountLatch(2)
        val minion1: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                latch.release()
                logger().debug("Minion 1 was started.")
            }
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        val minion2: MinionImpl = mockk(relaxed = true) {
            coEvery { start() } coAnswers {
                latch.release()
                logger().debug("Minion 2 was started.")
            }
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        minionsKeeper.getProperty<MutableMap<MinionId, MutableList<MinionImpl>>>("minions")["my-minion"] =
            mutableListOf(minion1, minion2)
        minionsKeeper.getProperty<MutableMap<CampaignId, SuspendedCountLatch>>(
            "minionsCountLatchesByCampaign"
        )["my-campaign"] = runningMinionsLatch

        // when
        val duration = coMeasureTime {
            minionsKeeper.startMinionAt("my-minion", Instant.now().plusMillis(100))
            latch.await()
        }

        // then
        coVerifyOnce {
            minion1.start()
            minion2.start()
            campaignStateKeeper.recordStartedMinion(eq("my-campaign"), eq("my-scenario"), eq(2))
        }
        QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(100), duration)
        confirmVerified(eventsLogger, meterRegistry, campaignStateKeeper)
    }
}
