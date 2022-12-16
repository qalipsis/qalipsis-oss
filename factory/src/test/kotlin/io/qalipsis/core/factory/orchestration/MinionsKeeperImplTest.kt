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
import io.micrometer.core.instrument.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.collections.ConcurrentTable
import io.qalipsis.core.collections.Table
import io.qalipsis.core.factory.testDag
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
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
    private lateinit var campaignMeterRegistry: CampaignMeterRegistry

    @RelaxedMockK("idleMinionsGauge")
    private lateinit var idleMinionsGauge: AtomicInteger

    @RelaxedMockK("runningMinionsGauge")
    private lateinit var runningMinionsGauge: AtomicInteger

    @RelaxedMockK
    private lateinit var reportLiveStateRegistry: CampaignReportLiveStateRegistry

    @BeforeEach
    internal fun setUp() {
        every {
            campaignMeterRegistry.gauge(
                "idle-minions",
                listOf(Tag.of("scenario", "my-scenario")),
                any<AtomicInteger>()
            )
        } returns idleMinionsGauge
        every {
            campaignMeterRegistry.gauge(
                "running-minions",
                listOf(Tag.of("scenario", "my-scenario")),
                any<AtomicInteger>()
            )
        } returns runningMinionsGauge
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
                campaignMeterRegistry,
                reportLiveStateRegistry,
                this
            )

            // when
            minionsKeeper.create("my-campaign", "my-scenario", listOf("my-dag-1", "my-dag-2"), "my-minion")
            runnerCountDown.await()

            // then
            coVerifyOnce {
                campaignMeterRegistry.gauge(
                    "idle-minions",
                    listOf(Tag.of("scenario", "my-scenario")),
                    any<AtomicInteger>()
                )
                eventsLogger.debug(
                    "minion.created", timestamp = any(),
                    tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
                )
                runner.run(any(), refEq(dag2))
            }
            confirmVerified(runner, eventsLogger, campaignMeterRegistry, reportLiveStateRegistry)

            assertThat(minionSlot.captured).all {
                prop(MinionImpl::id).isEqualTo("my-minion")
                prop(MinionImpl::campaignKey).isEqualTo("my-campaign")
                prop(MinionImpl::scenarioName).isEqualTo("my-scenario")
                prop(MinionImpl::isSingleton).isFalse()
                prop(MinionImpl::isStarted).isFalse()
            }
            assertThat(minionsKeeper).all {
                typedProp<Map<MinionId, MinionImpl>>("minions").key("my-minion").isSameAs(minionSlot.captured)
                typedProp<Map<MinionId, DirectedAcyclicGraphName>>("rootDagsOfMinions").key("my-minion")
                    .isEqualTo("my-dag-2")
                typedProp<Map<ScenarioName, MutableCollection<MinionImpl>>>("idleSingletonsMinions").isEmpty()
                typedProp<Table<ScenarioName, DirectedAcyclicGraphName, MinionImpl>>("singletonMinionsByDagId").transform { it.isEmpty() }
                    .isTrue()
                typedProp<Map<MinionId, Pair<ScenarioName, DirectedAcyclicGraphName>>>("dagIdsBySingletonMinionId").isEmpty()
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
                campaignMeterRegistry,
                reportLiveStateRegistry,
                coroutineScope
            )

            // when
            minionsKeeper.create("my-campaign", "my-scenario", listOf("my-dag-1", "my-dag-2"), "my-minion")

            // then
            confirmVerified(runner, eventsLogger, campaignMeterRegistry, reportLiveStateRegistry, coroutineScope)

            assertThat(minionsKeeper).all {
                typedProp<Map<MinionId, MinionImpl>>("minions").key("my-minion").isNotNull().all {
                    prop(MinionImpl::id).isEqualTo("my-minion")
                    prop(MinionImpl::campaignKey).isEqualTo("my-campaign")
                    prop(MinionImpl::scenarioName).isEqualTo("my-scenario")
                    prop(MinionImpl::isSingleton).isFalse()
                    prop(MinionImpl::isStarted).isTrue()
                }
                typedProp<Map<MinionId, DirectedAcyclicGraphName>>("rootDagsOfMinions").isEmpty()
                typedProp<Map<ScenarioName, MutableCollection<MinionImpl>>>("idleSingletonsMinions").isEmpty()
                typedProp<Table<ScenarioName, DirectedAcyclicGraphName, MinionImpl>>("singletonMinionsByDagId").transform { it.isEmpty() }
                    .isTrue()
                typedProp<Map<MinionId, Pair<ScenarioName, DirectedAcyclicGraphName>>>("dagIdsBySingletonMinionId").isEmpty()
            }
            Assertions.assertTrue(
                minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions").containsKey("my-minion")
            )
        }

    @Test
    @Timeout(2)
    internal fun `should create singleton paused minion on root dag`() = testCoroutineDispatcher.run {
        // given
        val dag = testDag(id = "my-dag-1", isSingleton = true, root = true)
        every { scenarioRegistry.get("my-scenario")?.get("my-dag-1") } returns dag
        val minionSlot = slot<MinionImpl>()
        val runnerCountDown = SuspendedCountLatch(1)
        coEvery { runner.run(capture(minionSlot), refEq(dag)) } coAnswers { runnerCountDown.decrement() }

        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            campaignMeterRegistry,
            reportLiveStateRegistry,
            this
        )

        // when
        minionsKeeper.create("my-campaign", "my-scenario", listOf("my-dag-1"), "my-minion")
        runnerCountDown.await()

        // then
        coVerifyOnce {
            eventsLogger.debug(
                "minion.created", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion")
            )
            runner.run(any(), refEq(dag))
            campaignMeterRegistry.gauge("idle-minions", listOf(Tag.of("scenario", "my-scenario")), any<AtomicInteger>())
            idleMinionsGauge.incrementAndGet()
        }

        confirmVerified(runner, eventsLogger, campaignMeterRegistry, reportLiveStateRegistry)

        assertThat(minionSlot.captured).all {
            prop(MinionImpl::id).isEqualTo("my-minion")
            prop(MinionImpl::campaignKey).isEqualTo("my-campaign")
            prop(MinionImpl::scenarioName).isEqualTo("my-scenario")
            prop(MinionImpl::isSingleton).isTrue()
            prop(MinionImpl::isStarted).isFalse()
        }
        assertThat(minionsKeeper).all {
            typedProp<Map<MinionId, MinionImpl>>("minions").key("my-minion").isSameAs(minionSlot.captured)
            typedProp<Map<MinionId, DirectedAcyclicGraphName>>("rootDagsOfMinions").key("my-minion")
                .isEqualTo("my-dag-1")
            typedProp<Map<ScenarioName, MutableCollection<MinionImpl>>>("idleSingletonsMinions").key("my-scenario")
                .containsOnly(minionSlot.captured)
            typedProp<Table<ScenarioName, DirectedAcyclicGraphName, MinionImpl>>("singletonMinionsByDagId").transform { it["my-scenario", "my-dag-1"] }
                .isSameAs(minionSlot.captured)
            typedProp<Map<MinionId, Pair<ScenarioName, DirectedAcyclicGraphName>>>("dagIdsBySingletonMinionId").key("my-minion")
                .isEqualTo("my-scenario" to "my-dag-1")
        }
    }

    @Test
    @Timeout(2)
    internal fun `should create singleton paused minion on non root dag`() = testCoroutineDispatcher.run {
        // given
        val dag = testDag(id = "my-dag-1", isSingleton = true, root = false)
        every { scenarioRegistry.get("my-scenario")?.get("my-dag-1") } returns dag

        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            campaignMeterRegistry,
            reportLiveStateRegistry,
            this
        )

        // when
        minionsKeeper.create("my-campaign", "my-scenario", listOf("my-dag-1"), "my-minion")

        // then
        confirmVerified(runner, eventsLogger, campaignMeterRegistry, reportLiveStateRegistry)

        val createdMinion = minionsKeeper.getProperty<Map<MinionId, MinionImpl>>("minions")["my-minion"]
        assertThat(createdMinion).isNotNull().all {
            prop(MinionImpl::id).isEqualTo("my-minion")
            prop(MinionImpl::campaignKey).isEqualTo("my-campaign")
            prop(MinionImpl::scenarioName).isEqualTo("my-scenario")
            prop(MinionImpl::isSingleton).isTrue()
            prop(MinionImpl::isStarted).isFalse()
        }
        assertThat(minionsKeeper).all {
            typedProp<Map<MinionId, DirectedAcyclicGraphName>>("rootDagsOfMinions").isEmpty()
            typedProp<Map<ScenarioName, MutableCollection<MinionImpl>>>("idleSingletonsMinions").key("my-scenario")
                .containsOnly(createdMinion)
            typedProp<Table<ScenarioName, DirectedAcyclicGraphName, MinionImpl>>("singletonMinionsByDagId").transform { it["my-scenario", "my-dag-1"] }
                .isSameAs(createdMinion)
            typedProp<Map<MinionId, Pair<ScenarioName, DirectedAcyclicGraphName>>>("dagIdsBySingletonMinionId").key("my-minion")
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
            campaignMeterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minion1: MinionImpl = relaxedMockk()
        val minion2: MinionImpl = relaxedMockk()
        listOf(minion1, minion2).forEach {
            every { it.campaignKey } returns "my-campaign"
            every { it.scenarioName } returns "my-scenario"
        }
        val idleSingletonsMinions =
            minionsKeeper.getProperty<MutableMap<ScenarioName, List<MinionImpl>>>("idleSingletonsMinions")
        idleSingletonsMinions["my-scenario"] = mutableListOf(minion1, minion2)

        // when
        minionsKeeper.startSingletons("my-scenario")

        // then
        coVerify {
            minion1.start()
            minion2.start()
        }
        assertThat(idleSingletonsMinions).isEmpty()

        confirmVerified(scenarioRegistry, eventsLogger, campaignMeterRegistry, reportLiveStateRegistry)
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
                campaignMeterRegistry,
                reportLiveStateRegistry,
                this
            )
            // when
            assertDoesNotThrow {
                minionsKeeper.startSingletons("my-scenario")
            }

            // then
            confirmVerified(scenarioRegistry, eventsLogger, campaignMeterRegistry, reportLiveStateRegistry)
        }

    @Test
    @Timeout(2)
    internal fun `should schedule immediate minion start when start time is now`() {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            campaignMeterRegistry,
            reportLiveStateRegistry,
            relaxedMockk()
        )
        val startTime = AtomicLong()
        val latch = SuspendedCountLatch(1)
        val minionToStart1 = relaxedMockk<MinionImpl> { every { id } returns "my-minion-1" }
        val minionToStart2 = relaxedMockk<MinionImpl> { every { id } returns "my-minion-2" }
        val minionToIgnore = relaxedMockk<MinionImpl> { every { id } returns "my-other" }
        listOf(minionToStart1, minionToStart2, minionToIgnore).forEach {
            every { it.campaignKey } returns "my-campaign"
            every { it.scenarioName } returns "my-scenario"
            coEvery { it.start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
        }
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        minions["my-minion-1"] = minionToStart1
        minions["my-minion-2"] = minionToStart2
        minions["other-minion"] = minionToIgnore

        // when
        val duration = coMeasureTime {
            minionsKeeper.scheduleMinionStart(Instant.now(), listOf("my-minion-1", "my-minion-2"))
            latch.await()
        }

        // then
        coVerifyOrder {
            minionToStart1.start()
            eventsLogger.debug(
                "minion.started", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion-1")
            )
            minionToStart2.start()
            eventsLogger.debug(
                "minion.started", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion-2")
            )
            reportLiveStateRegistry.recordStartedMinion("my-campaign", "my-scenario", 2)
            campaignMeterRegistry.gauge("idle-minions", listOf(Tag.of("scenario", "my-scenario")), any<AtomicInteger>())
            idleMinionsGauge.addAndGet(-2)
            campaignMeterRegistry.gauge(
                "running-minions",
                listOf(Tag.of("scenario", "my-scenario")),
                any<AtomicInteger>()
            )
            runningMinionsGauge.addAndGet(2)
        }
        QalipsisTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(200), duration)
        confirmVerified(eventsLogger, campaignMeterRegistry, reportLiveStateRegistry, minionToIgnore)
    }

    @Test
    @Timeout(2)
    internal fun `should schedule minion start later when start time is not reached`() {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            campaignMeterRegistry,
            reportLiveStateRegistry,
            relaxedMockk()
        )
        val startTime = AtomicLong()
        val latch = SuspendedCountLatch(1)
        val minionToStart1 = relaxedMockk<MinionImpl> { every { id } returns "my-minion-1" }
        val minionToStart2 = relaxedMockk<MinionImpl> { every { id } returns "my-minion-2" }
        val minionToIgnore = relaxedMockk<MinionImpl> { every { id } returns "my-other" }
        listOf(minionToStart1, minionToStart2, minionToIgnore).forEach {
            every { it.campaignKey } returns "my-campaign"
            every { it.scenarioName } returns "my-scenario"
            coEvery { it.start() } coAnswers {
                startTime.set(System.currentTimeMillis())
                latch.release()
            }
        }
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        minions["my-minion-1"] = minionToStart1
        minions["my-minion-2"] = minionToStart2
        minions["other-minion"] = minionToIgnore

        // when
        val duration = coMeasureTime {
            minionsKeeper.scheduleMinionStart(Instant.now().plusMillis(400), listOf("my-minion-1", "my-minion-2"))
            latch.await()
        }

        // then
        coVerifyOnce {
            minionToStart1.start()
            eventsLogger.debug(
                "minion.started", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion-1")
            )
            minionToStart2.start()
            eventsLogger.debug(
                "minion.started", timestamp = any(),
                tags = mapOf("campaign" to "my-campaign", "scenario" to "my-scenario", "minion" to "my-minion-2")
            )
            reportLiveStateRegistry.recordStartedMinion("my-campaign", "my-scenario", 2)
            campaignMeterRegistry.gauge("idle-minions", listOf(Tag.of("scenario", "my-scenario")), any<AtomicInteger>())
            idleMinionsGauge.addAndGet(-2)
            campaignMeterRegistry.gauge(
                "running-minions",
                listOf(Tag.of("scenario", "my-scenario")),
                any<AtomicInteger>()
            )
            runningMinionsGauge.addAndGet(2)
        }
        QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(350), duration)
        confirmVerified(eventsLogger, campaignMeterRegistry, reportLiveStateRegistry, minionToIgnore)
    }

    @Test
    internal fun `should restart existing minion`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            campaignMeterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val minion = relaxedMockk<MinionImpl> {
            every { scenarioName } returns "my-scenario"
        }
        minions["my-minion"] = minion
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphName>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion"] = "my-dag"
        val dag = relaxedMockk<DirectedAcyclicGraph>()
        every { scenarioRegistry["my-scenario"]!!["my-dag"] } returns dag

        // when
        minionsKeeper.restartMinion("my-minion")

        // then
        coVerifyOrder {
            minion.restart(true)
            minion.scenarioName
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
            campaignMeterRegistry,
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
                campaignMeterRegistry,
                reportLiveStateRegistry,
                this
            )
            val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
            val minion = relaxedMockk<MinionImpl> {
                every { scenarioName } returns "my-scenario"
            }
            minions["my-minion"] = minion

            // when
            minionsKeeper.restartMinion("my-minion")

            // then
            coVerifyOrder {
                minion.restart(true)
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
            campaignMeterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val minion = relaxedMockk<MinionImpl> {
            every { id } returns "my-minion"
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
            every { isSingleton } returns true
        }
        minions["my-minion"] = minion
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphName>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion"] = "my-dag"
        val dagIdsBySingletonMinionId =
            minionsKeeper.getProperty<MutableMap<MinionId, Pair<ScenarioName, DirectedAcyclicGraphName>>>("dagIdsBySingletonMinionId")
        dagIdsBySingletonMinionId["my-minion"] = "my-scenario" to "my-dag"
        val singletonMinionsByDagId =
            minionsKeeper.getProperty<ConcurrentTable<ScenarioName, DirectedAcyclicGraphName, MinionImpl>>("singletonMinionsByDagId")
        singletonMinionsByDagId.put("my-scenario", "my-dag", relaxedMockk())

        // when
        minionsKeeper.shutdownMinion("my-minion")

        // then
        coVerifyOrder {
            minion.id
            minion.campaignKey
            minion.scenarioName
            eventsLogger.debug(
                "minion.completion.in-progress",
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion",
                    "interrupt" to "false"
                )
            )
            minion.isSingleton
            minion.stop(false)
            eventsLogger.debug(
                "minion.completion.done",
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion",
                    "interrupt" to "false"
                )
            )
        }
        assertThat(rootDagsOfMinions["my-minion"]).isNull()
        assertThat(dagIdsBySingletonMinionId["my-minion"]).isNull()
        assertThat(singletonMinionsByDagId["my-minion", "my-dag"]).isNull()
        confirmVerified(minion, runner, eventsLogger, reportLiveStateRegistry)
    }

    @Test
    internal fun `should shutdown existing minion under load with failure`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            campaignMeterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val theException = RuntimeException()
        val minion = relaxedMockk<MinionImpl> {
            every { id } returns "my-minion"
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
            every { isSingleton } returns false
            coEvery { stop(false) } throws theException
        }
        minions["my-minion"] = minion
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphName>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion"] = "my-dag"

        // when
        minionsKeeper.shutdownMinion("my-minion")

        // then
        coVerifyOrder {
            minion.id
            minion.campaignKey
            minion.scenarioName
            eventsLogger.debug(
                "minion.completion.in-progress",
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion",
                    "interrupt" to "false"
                )
            )
            minion.isSingleton
            reportLiveStateRegistry.recordCompletedMinion("my-campaign", "my-scenario", 1)
            minion.stop(false)
            eventsLogger.debug(
                "minion.completion.failed",
                value = refEq(theException),
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion",
                    "interrupt" to "false"
                )
            )
        }
        assertThat(rootDagsOfMinions["my-minion"]).isNull()
        confirmVerified(minion, runner, eventsLogger, reportLiveStateRegistry)
    }

    @Test
    internal fun `should ignore shutdown of absent minion`() = testCoroutineDispatcher.runTest {
        // given
        val minionsKeeper = MinionsKeeperImpl(
            scenarioRegistry,
            runner,
            eventsLogger,
            campaignMeterRegistry,
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
            campaignMeterRegistry,
            reportLiveStateRegistry,
            this
        )
        val minions = minionsKeeper.getProperty<MutableMap<MinionId, MinionImpl>>("minions")
        val theException = RuntimeException()
        val minion1 = relaxedMockk<MinionImpl>("minion-1") {
            every { id } returns "my-minion1"
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
            every { isSingleton } returns false
            coEvery { stop(true) } throws theException
        }
        val minion2 = relaxedMockk<MinionImpl>("minion-2") {
            every { id } returns "my-minion2"
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
            every { isSingleton } returns true
        }
        val minion3 = relaxedMockk<MinionImpl>("minion-3") {
            every { id } returns "my-minion3"
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
            every { isSingleton } returns false
        }
        minions["my-minion1"] = minion1
        minions["my-minion2"] = minion2
        minions["my-minion3"] = minion3
        val rootDagsOfMinions =
            minionsKeeper.getProperty<MutableMap<MinionId, DirectedAcyclicGraphName>>("rootDagsOfMinions")
        rootDagsOfMinions["my-minion1"] = "my-dag"
        val dagIdsBySingletonMinionId =
            minionsKeeper.getProperty<MutableMap<MinionId, Pair<ScenarioName, DirectedAcyclicGraphName>>>("dagIdsBySingletonMinionId")
        dagIdsBySingletonMinionId["my-minion1"] = "my-scenario" to "my-dag"
        val singletonMinionsByDagId =
            minionsKeeper.getProperty<ConcurrentTable<ScenarioName, DirectedAcyclicGraphName, MinionImpl>>("singletonMinionsByDagId")
        singletonMinionsByDagId.put("my-scenario", "my-dag", relaxedMockk())
        val idleMinionsGauges =
            minionsKeeper.getProperty<MutableMap<ScenarioName, AtomicInteger>>("idleMinionsGauges")
        idleMinionsGauges["my-scenario"] = AtomicInteger(123)
        val runningMinionsGauges =
            minionsKeeper.getProperty<MutableMap<ScenarioName, AtomicInteger>>("runningMinionsGauges")
        runningMinionsGauges["my-scenario"] = AtomicInteger(76543)

        // when
        minionsKeeper.shutdownAll()

        // then
        coVerifyExactly(2) {
            reportLiveStateRegistry.recordCompletedMinion("my-campaign", "my-scenario")
        }
        coVerifyOnce {
            minion1.id
            minion1.campaignKey
            minion1.scenarioName
            eventsLogger.debug(
                "minion.completion.in-progress",
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion1",
                    "interrupt" to "true"
                )
            )
            minion1.isSingleton
            minion1.stop(true)
            eventsLogger.debug(
                "minion.completion.failed",
                value = refEq(theException),
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion1",
                    "interrupt" to "true"
                )
            )

            minion2.id
            minion2.campaignKey
            minion2.scenarioName
            eventsLogger.debug(
                "minion.completion.in-progress",
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion2",
                    "interrupt" to "true"
                )
            )
            minion2.isSingleton
            minion2.stop(true)
            eventsLogger.debug(
                "minion.completion.done",
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion2",
                    "interrupt" to "true"
                )
            )

            minion3.id
            minion3.campaignKey
            minion3.scenarioName
            eventsLogger.debug(
                "minion.completion.in-progress",
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion3",
                    "interrupt" to "true"
                )
            )
            minion3.isSingleton
            minion3.stop(true)
            eventsLogger.debug(
                "minion.completion.done",
                timestamp = any(),
                tags = mapOf(
                    "campaign" to "my-campaign",
                    "scenario" to "my-scenario",
                    "minion" to "my-minion3",
                    "interrupt" to "true"
                )
            )
        }
        assertThat(minions).isEmpty()
        assertThat(rootDagsOfMinions).isEmpty()
        assertThat(dagIdsBySingletonMinionId).isEmpty()
        assertThat(singletonMinionsByDagId.isEmpty()).isTrue()
        assertThat(idleMinionsGauges).isEmpty()
        assertThat(runningMinionsGauges).isEmpty()
        confirmVerified(minion1, minion2, minion3, runner, eventsLogger)
    }
}
