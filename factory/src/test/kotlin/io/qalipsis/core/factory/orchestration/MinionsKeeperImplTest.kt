package io.qalipsis.core.factory.orchestration

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.collections.ConcurrentTable
import io.qalipsis.core.collections.Table
import io.qalipsis.core.factory.testDag
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.time.QalipsisTimeAssertions
import io.qalipsis.test.time.coMeasureTime
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Eric Jessé
 */
@WithMockk
internal class MinionsKeeperImplTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var runner: RunnerImpl

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var executingStepsGauge: AtomicInteger

    @RelaxedMockK
    private lateinit var reportLiveStateRegistry: CampaignReportLiveStateRegistry

    @BeforeEach
    internal fun setUp() {
        every { meterRegistry.gauge("minion-running-steps", any(), any<AtomicInteger>()) } returns executingStepsGauge
    }

    @Test
    @Timeout(2)
    internal fun `should create paused minion when executes on root DAG under load`() =
        testCoroutineDispatcher.run {
            // given
            val dag1 = testDag(id = "my-dag-1", isUnderLoad = true, root = false)
            val dag2 = testDag(id = "my-dag-2", isUnderLoad = true, root = true)
            every { scenarioRegistry.get("my-scenario")?.get("my-dag-1") } returns dag1
            every { scenarioRegistry.get("my-scenario")?.get("my-dag-2") } returns dag2

            val minionSlot = slot<MinionImpl>()
            val runnerCountDown = SuspendedCountLatch(1)
            coEvery { runner.run(capture(minionSlot), refEq(dag2)) } coAnswers { runnerCountDown.decrement() }

            val minionsKeeper = MinionsKeeperImpl(
                scenarioRegistry,
                runner,
                eventsLogger,
                meterRegistry,
                reportLiveStateRegistry,
                this
            )

            // when
            minionsKeeper.create("my-campaign", "my-scenario", listOf("my-dag-1", "my-dag-2"), "my-minion")
            runnerCountDown.await()

            // then
            coVerifyOnce {
                meterRegistry.gauge(
                    "minion-running-steps",
                    listOf(
                        Tag.of("campaign", "my-campaign"),
                        Tag.of("scenario", "my-scenario"),
                        Tag.of("minion", "my-minion")
                    ), any<AtomicInteger>()
                )
                eventsLogger.info(
                    "minion.created", timestamp = any(),
                    tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
                )
                runner.run(any(), refEq(dag2))
            }
            confirmVerified(runner, eventsLogger, meterRegistry, reportLiveStateRegistry)

            assertThat(minionSlot.captured).all {
                prop(MinionImpl::id).isEqualTo("my-minion")
                prop(MinionImpl::campaignId).isEqualTo("my-campaign")
                prop(MinionImpl::scenarioId).isEqualTo("my-scenario")
                prop(MinionImpl::isSingleton).isFalse()
                prop("executingStepsGauge").isSameAs(executingStepsGauge)
                prop(MinionImpl::isStarted).isFalse()
            }
            assertThat(minionsKeeper).all {
                typedProp<Map<MinionId, MinionImpl>>("minions").key("my-minion").isSameAs(minionSlot.captured)
                typedProp<Map<MinionId, DirectedAcyclicGraphId>>("rootDagsOfMinions").key("my-minion")
                    .isEqualTo("my-dag-2")
                typedProp<Map<ScenarioId, MutableCollection<MinionImpl>>>("idleSingletonsMinions").isEmpty()
                typedProp<Table<ScenarioId, DirectedAcyclicGraphId, MinionImpl>>("singletonMinionsByDagId").transform { it.isEmpty() }
                    .isTrue()
                typedProp<Map<MinionId, Pair<ScenarioId, DirectedAcyclicGraphId>>>("dagIdsBySingletonMinionId").isEmpty()
            }
        }

    @Test
    @Timeout(2)
    internal fun `should create unpaused minion when executes on non-root DAG under load and records no creation event`() =
        testCoroutineDispatcher.runTest {
            // given
            val dag1 = testDag(id = "my-dag-1", isUnderLoad = true, root = false)
            val dag2 = testDag(id = "my-dag-2", isUnderLoad = true, root = false)
            every { scenarioRegistry.get("my-scenario")?.get("my-dag-1") } returns dag1
            every { scenarioRegistry.get("my-scenario")?.get("my-dag-2") } returns dag2
            val coroutineScope = relaxedMockk<CoroutineScope>()

            val minionsKeeper = MinionsKeeperImpl(
                scenarioRegistry,
                runner,
                eventsLogger,
                meterRegistry,
                reportLiveStateRegistry,
                coroutineScope
            )

            // when
            minionsKeeper.create("my-campaign", "my-scenario", listOf("my-dag-1", "my-dag-2"), "my-minion")

            // then
            coVerifyOnce {
                meterRegistry.gauge(
                    "minion-running-steps",
                    listOf(
                        Tag.of("campaign", "my-campaign"),
                        Tag.of("scenario", "my-scenario"),
                        Tag.of("minion", "my-minion")
                    ), any<AtomicInteger>()
                )
            }
            confirmVerified(runner, eventsLogger, meterRegistry, reportLiveStateRegistry, coroutineScope)

            assertThat(minionsKeeper).all {
                typedProp<Map<MinionId, MinionImpl>>("minions").key("my-minion").isNotNull().all {
                    prop(MinionImpl::id).isEqualTo("my-minion")
                    prop(MinionImpl::campaignId).isEqualTo("my-campaign")
                    prop(MinionImpl::scenarioId).isEqualTo("my-scenario")
                    prop(MinionImpl::isSingleton).isFalse()
                    prop("executingStepsGauge").isSameAs(executingStepsGauge)
                    prop(MinionImpl::isStarted).isTrue()
                }
                typedProp<Map<MinionId, DirectedAcyclicGraphId>>("rootDagsOfMinions").isEmpty()
                typedProp<Map<ScenarioId, MutableCollection<MinionImpl>>>("idleSingletonsMinions").isEmpty()
                typedProp<Table<ScenarioId, DirectedAcyclicGraphId, MinionImpl>>("singletonMinionsByDagId").transform { it.isEmpty() }
                    .isTrue()
                typedProp<Map<MinionId, Pair<ScenarioId, DirectedAcyclicGraphId>>>("dagIdsBySingletonMinionId").isEmpty()
            }
            Assertions.assertTrue(
                minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions").containsKey("my-minion")
            )
        }

    @Test
    @Timeout(2)
    internal fun `should create singleton paused minion`() = testCoroutineDispatcher.runTest {
        // given
        val dag = testDag(id = "my-dag-1", isSingleton = true)
        every { scenarioRegistry.get("my-scenario")?.get("my-dag-1") } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = SuspendedCountLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } coAnswers { runnerCountDown.decrement() }

        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )

        // when
        minionsKeeper.create("my-campaign", "my-scenario", listOf("my-dag-1"), "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            meterRegistry.gauge(
                "minion-running-steps",
                listOf(
                    Tag.of("campaign", "my-campaign"),
                    Tag.of("scenario", "my-scenario"),
                    Tag.of("minion", "my-minion")
                ), any<AtomicInteger>()
            )
            eventsLogger.info(
                "minion.created", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
            runner.run(any(), refEq(dag))
        }

        confirmVerified(runner, eventsLogger, meterRegistry, reportLiveStateRegistry)

        assertThat(minionSlot.captured).all {
            prop(MinionImpl::id).isEqualTo("my-minion")
            prop(MinionImpl::campaignId).isEqualTo("my-campaign")
            prop(MinionImpl::scenarioId).isEqualTo("my-scenario")
            prop(MinionImpl::isSingleton).isTrue()
            prop("executingStepsGauge").isSameAs(executingStepsGauge)
            prop(MinionImpl::isStarted).isFalse()
        }
        assertThat(minionsKeeper).all {
            typedProp<Map<MinionId, MinionImpl>>("minions").key("my-minion").isSameAs(minionSlot.captured)
            typedProp<Map<MinionId, DirectedAcyclicGraphId>>("rootDagsOfMinions").key("my-minion").isEqualTo("my-dag-1")
            typedProp<Map<ScenarioId, MutableCollection<MinionImpl>>>("idleSingletonsMinions").key("my-scenario")
                .containsOnly(minionSlot.captured)
            typedProp<Table<ScenarioId, DirectedAcyclicGraphId, MinionImpl>>("singletonMinionsByDagId").transform { it["my-scenario", "my-dag-1"] }
                .isSameAs(minionSlot.captured)
            typedProp<Map<MinionId, Pair<ScenarioId, DirectedAcyclicGraphId>>>("dagIdsBySingletonMinionId").key("my-minion")
                .isEqualTo("my-scenario" to "my-dag-1")
        }
    }

    @Test
    @Timeout(2)
    internal fun `should start singletons immediately`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minion1: MinionImpl = relaxedMockk()
        val minion2: MinionImpl = relaxedMockk()
        listOf(minion1, minion2).forEach {
            every { it.campaignId } returns "my-campaign"
            every { it.scenarioId } returns "my-scenario"
        }
        val idleSingletonsMinions =
            minionsKeeper.getProperty<MutableMap<ScenarioId, List<MinionImpl>>>("idleSingletonsMinions")
        idleSingletonsMinions["my-scenario"] = mutableListOf(minion1, minion2)

        // when
        minionsKeeper.startSingletons("my-scenario")

        // then
        coVerify {
            minion1.start()
            minion2.start()
        }
        assertThat(idleSingletonsMinions).isEmpty()

        confirmVerified(scenarioRegistry, eventsLogger, meterRegistry, reportLiveStateRegistry)
    }

    @Test
    @Timeout(2)
    internal fun `should ignore singletons start when none exists for the scenario`() =
        testCoroutineDispatcher.runTest {
            // given
            val minionsKeeper = MinionsKeeperImpl(
                scenarioRegistry,
                runner,
                eventsLogger,
                meterRegistry,
                reportLiveStateRegistry,
                this
            )
            // when
            assertDoesNotThrow {
                minionsKeeper.startSingletons("my-scenario")
            }

            // then
            confirmVerified(scenarioRegistry, eventsLogger, meterRegistry, reportLiveStateRegistry)
        }

    @Test
    @Timeout(2)
    internal fun `should schedule immediate minion start when start time is now`() {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            relaxedMockk()
        )
        val startTime = AtomicLong()
        val latch = SuspendedCountLatch(1)
        val minionToStart = relaxedMockk<MinionImpl> { every { id } returns "my-minion" }
        val minionToIgnore = relaxedMockk<MinionImpl> { every { id } returns "my-other" }
        listOf(minionToStart, minionToIgnore).forEach {
            every { it.campaignId } returns "my-campaign"
            every { it.scenarioId } returns "my-scenario"
            coEvery { it.start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
        }
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        minions["my-minion"] = minionToStart
        minions["other-minion"] = minionToIgnore

        // when
        val duration = coMeasureTime {
            minionsKeeper.scheduleMinionStart("my-minion", Instant.now())
            latch.await()
        }

        // then
        coVerifyOnce {
            minionToStart.start()
            reportLiveStateRegistry.recordStartedMinion("my-campaign", "my-scenario", 1)
            eventsLogger.info(
                "minion.started", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
        }
        QalipsisTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(80), duration)
        confirmVerified(eventsLogger, meterRegistry, reportLiveStateRegistry, minionToIgnore)
    }

    @Test
    @Timeout(2)
    internal fun `should schedule minion start later when start time is not reached`() {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            relaxedMockk()
        )
        val startTime = AtomicLong()
        val latch = SuspendedCountLatch(1)
        val minionToStart = relaxedMockk<MinionImpl> { every { id } returns "my-minion" }
        val minionToIgnore = relaxedMockk<MinionImpl> { every { id } returns "my-other" }
        listOf(minionToStart, minionToIgnore).forEach {
            every { it.campaignId } returns "my-campaign"
            every { it.scenarioId } returns "my-scenario"
            coEvery { it.start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
        }
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        minions["my-minion"] = minionToStart
        minions["other-minion"] = minionToIgnore

        // when
        val duration = coMeasureTime {
            minionsKeeper.scheduleMinionStart("my-minion", Instant.now().plusMillis(400))
            latch.await()
        }

        // then
        coVerifyOnce {
            minionToStart.start()
            reportLiveStateRegistry.recordStartedMinion("my-campaign", "my-scenario", 1)
            eventsLogger.info(
                "minion.started", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
        }
        QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(350), duration)
        confirmVerified(eventsLogger, meterRegistry, reportLiveStateRegistry, minionToIgnore)
    }

    @Test
    internal fun `should restart existing minion`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val minion = relaxedMockk<MinionImpl> {
            every { scenarioId } returns "my-scenario"
        }
        minions["my-minion"] = minion
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphId>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion"] = "my-dag"
        val dag = relaxedMockk<DirectedAcyclicGraph>()
        every { scenarioRegistry["my-scenario"]!!["my-dag"] } returns dag

        // when
        minionsKeeper.restartMinion("my-minion")

        // then
        coVerifyOrder {
            minion.cancel()
            minion.reset(true)
            minion.scenarioId
            runner.run(refEq(minion), refEq(dag))
            minion.start()
        }
        confirmVerified(minion, runner)
    }

    @Test
    internal fun `should restart existing minion even if cancel fails`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val minion = relaxedMockk<MinionImpl> {
            every { scenarioId } returns "my-scenario"
            coEvery { cancel() } throws RuntimeException()
        }
        minions["my-minion"] = minion
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphId>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion"] = "my-dag"
        val dag = relaxedMockk<DirectedAcyclicGraph>()
        every { scenarioRegistry["my-scenario"]!!["my-dag"] } returns dag

        // when
        minionsKeeper.restartMinion("my-minion")

        // then
        coVerifyOrder {
            minion.cancel()
            minion.reset(true)
            minion.scenarioId
            runner.run(refEq(minion), refEq(dag))
            minion.start()
        }
        confirmVerified(minion, runner)
    }

    @Test
    internal fun `should ignore restart of absent minion`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )

        // when
        assertDoesNotThrow {
            minionsKeeper.restartMinion("my-minion")
        }

        // then
        confirmVerified(runner)
    }

    @Test
    internal fun `should restart minion without DAG without executing anything on it`() =
        testCoroutineDispatcher.runTest {
            // given
            val minionsKeeper = MinionsKeeperImpl(
                scenarioRegistry,
                runner,
                eventsLogger,
                meterRegistry,
                reportLiveStateRegistry,
                this
            )
            val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
            val minion = relaxedMockk<MinionImpl> {
                every { scenarioId } returns "my-scenario"
            }
            minions["my-minion"] = minion

            // when
            minionsKeeper.restartMinion("my-minion")

            // then
            coVerifyOrder {
                minion.cancel()
                minion.reset(true)
                minion.start()
            }
            confirmVerified(minion, runner)
        }

    @Test
    internal fun `should shutdown existing singleton minion`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val minion = relaxedMockk<MinionImpl> {
            every { id } returns "my-minion"
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        minions["my-minion"] = minion
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphId>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion"] = "my-dag"
        val dagIdsBySingletonMinionId =
            minionsKeeper.getProperty<MutableMap<MinionId, Pair<ScenarioId, DirectedAcyclicGraphId>>>("dagIdsBySingletonMinionId")
        dagIdsBySingletonMinionId["my-minion"] = "my-scenario" to "my-dag"
        val singletonMinionsByDagId =
            minionsKeeper.getProperty<ConcurrentTable<ScenarioId, DirectedAcyclicGraphId, MinionImpl>>("singletonMinionsByDagId")
        singletonMinionsByDagId.put("my-scenario", "my-dag", relaxedMockk())

        // when
        minionsKeeper.shutdownMinion("my-minion")

        // then
        coVerifyOrder {
            minion.id
            minion.campaignId
            minion.scenarioId
            eventsLogger.info(
                "minion.cancellation.started",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
            minion.cancel()
            eventsLogger.info(
                "minion.cancellation.complete",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
        }
        assertThat(rootDagsOfMinions["my-minion"]).isNull()
        assertThat(dagIdsBySingletonMinionId["my-minion"]).isNull()
        assertThat(singletonMinionsByDagId["my-minion", "my-dag"]).isNull()
        confirmVerified(minion, runner, eventsLogger)
    }

    @Test
    internal fun `should shutdown existing minion under load with failure`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val theException = RuntimeException()
        val minion = relaxedMockk<MinionImpl> {
            every { id } returns "my-minion"
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
            coEvery { cancel() } throws theException
        }
        minions["my-minion"] = minion
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphId>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion"] = "my-dag"

        // when
        minionsKeeper.shutdownMinion("my-minion")

        // then
        coVerifyOrder {
            minion.id
            minion.campaignId
            minion.scenarioId
            eventsLogger.info(
                "minion.cancellation.started",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
            minion.cancel()
            eventsLogger.info(
                "minion.cancellation.complete",
                value = refEq(theException),
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
        }
        assertThat(rootDagsOfMinions["my-minion"]).isNull()
        confirmVerified(minion, runner, eventsLogger)
    }

    @Test
    internal fun `should ignore shutdown of absent minion`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )

        // when
        assertDoesNotThrow {
            minionsKeeper.shutdownMinion("my-minion")
        }

        // then
        confirmVerified(runner, eventsLogger)
    }

    @Test
    internal fun `should shutdown all the minions`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val theException = RuntimeException()
        val minion1 = relaxedMockk<MinionImpl> {
            every { id } returns "my-minion1"
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
            coEvery { cancel() } throws theException
        }
        val minion2 = relaxedMockk<MinionImpl> {
            every { id } returns "my-minion2"
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        val minion3 = relaxedMockk<MinionImpl> {
            every { id } returns "my-minion3"
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        minions["my-minion1"] = minion1
        minions["my-minion2"] = minion2
        minions["my-minion3"] = minion3
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphId>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion1"] = "my-dag"
        val dagIdsBySingletonMinionId =
            minionsKeeper.getProperty<MutableMap<MinionId, Pair<ScenarioId, DirectedAcyclicGraphId>>>("dagIdsBySingletonMinionId")
        dagIdsBySingletonMinionId["my-minion1"] = "my-scenario" to "my-dag"
        val singletonMinionsByDagId =
            minionsKeeper.getProperty<ConcurrentTable<ScenarioId, DirectedAcyclicGraphId, MinionImpl>>("singletonMinionsByDagId")
        singletonMinionsByDagId.put("my-scenario", "my-dag", relaxedMockk())

        // when
        minionsKeeper.shutdownAll()

        // then
        coVerifyOnce {
            minion1.id
            minion1.campaignId
            minion1.scenarioId
            eventsLogger.info(
                "minion.cancellation.started",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion1")
            )
            minion1.cancel()
            eventsLogger.info(
                "minion.cancellation.complete",
                value = refEq(theException),
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion1")
            )

            minion2.id
            minion2.campaignId
            minion2.scenarioId
            eventsLogger.info(
                "minion.cancellation.started",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion2")
            )
            minion2.cancel()
            eventsLogger.info(
                "minion.cancellation.complete",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion2")
            )

            minion3.id
            minion3.campaignId
            minion3.scenarioId
            eventsLogger.info(
                "minion.cancellation.started",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion3")
            )
            minion3.cancel()
            eventsLogger.info(
                "minion.cancellation.complete",
                timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion3")
            )
        }
        assertThat(minions).isEmpty()
        assertThat(rootDagsOfMinions).isEmpty()
        assertThat(dagIdsBySingletonMinionId).isEmpty()
        assertThat(singletonMinionsByDagId.isEmpty()).isTrue()
        confirmVerified(minion1, minion2, minion3, runner, eventsLogger)
    }
}
