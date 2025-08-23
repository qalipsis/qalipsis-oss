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

package io.qalipsis.core.factory.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.executionprofile.MinionsStartingLine
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.orchestration.CampaignCompletionState
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.factory.redis.catadioptre.maxMinionsCountsByScenario
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
@ExperimentalLettuceCoroutinesApi
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.FACTORY], startApplication = false)
@PropertySource(
    Property(name = "factory.assignment.strategy", value = "distributed-minion")
)
internal class RedisDistributedMinionAssignmentKeeperIntegrationTest : AbstractRedisIntegrationTest() {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @MockBean(EventsLogger::class)
    fun eventsLogger() = eventsLogger

    @MockBean(LocalAssignmentStore::class)
    fun localAssignmentStore() = localAssignmentStore

    @MockBean(ScenarioRegistry::class)
    fun scenarioRegistry() = scenarioRegistry

    @BeforeEach
    internal fun setUp() {
        every { scenarioRegistry.get(SCENARIO_1)!!.dags } returns listOf(
            mockk {
                every { name } returns SCENARIO_1_DAG_1
                every { isRoot } returns true
                every { isUnderLoad } returns true
            }
        )
        every { scenarioRegistry.get(SCENARIO_2)!!.dags } returns listOf(
            mockk {
                every { name } returns SCENARIO_2_DAG_1
                every { isRoot } returns true
                every { isUnderLoad } returns true
            }
        )
    }

    @AfterAll
    internal fun tearDownAll() {
        connection.sync().flushdb()
    }

    @Test
    @Timeout(10)
    @Order(1)
    @MicronautTest(startApplication = false)
    @PropertySource(Property(name = "factory.assignment.timeout", value = "2s"))
    internal fun `should assign a lot of minions only for the DAGs supported by the factory`(minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper) =
        testDispatcherProvider.run {
            // given
            val factory1 = "the-factory-1"
            val factory2 = "the-factory-2"

            minionAssignmentKeeper.registerMinionsToAssign(
                CAMPAIGN,
                SCENARIO_1,
                listOf(SCENARIO_1_DAG_1, SCENARIO_1_DAG_2, SCENARIO_1_DAG_3, SCENARIO_1_DAG_4, SCENARIO_1_DAG_5),
                MINIONS_SCENARIO_1
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                CAMPAIGN,
                SCENARIO_1,
                listOf(SCENARIO_1_DAG_SINGLETON_1),
                listOf(MINIONS_SINGLETON_1),
                false
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                CAMPAIGN,
                SCENARIO_1,
                listOf(SCENARIO_1_DAG_SINGLETON_2),
                listOf(MINIONS_SINGLETON_2),
                false
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                CAMPAIGN,
                SCENARIO_2,
                listOf(SCENARIO_2_DAG_1, SCENARIO_2_DAG_2, SCENARIO_2_DAG_3, SCENARIO_2_DAG_4),
                MINIONS_SCENARIO_2
            )
            minionAssignmentKeeper.completeUnassignedMinionsRegistration(CAMPAIGN, SCENARIO_1)
            minionAssignmentKeeper.completeUnassignedMinionsRegistration(CAMPAIGN, SCENARIO_2)

            // when
            val minionsScenario1UnderLoad = minionAssignmentKeeper.getIdsOfMinionsUnderLoad(CAMPAIGN, SCENARIO_1)
            val minionsScenario2UnderLoad = minionAssignmentKeeper.getIdsOfMinionsUnderLoad(CAMPAIGN, SCENARIO_2)

            // then
            assertThat(minionsScenario1UnderLoad.sorted()).containsExactly(*MINIONS_SCENARIO_1.sorted().toTypedArray())
            assertThat(minionsScenario2UnderLoad.sorted()).containsExactly(*MINIONS_SCENARIO_2.sorted().toTypedArray())

            // when
            // Factory 1 has DAG-1, DAG-2 and DAG-3 for scenario 1, DAG-1 and DAG-2 for scenario 2.
            minionAssignmentKeeper.coInvokeInvisible<Unit>(
                "assignFactoryDags",
                CAMPAIGN,
                listOf(
                    FactoryScenarioAssignment(
                        SCENARIO_1, listOf(
                            SCENARIO_1_DAG_1,
                            SCENARIO_1_DAG_2,
                            SCENARIO_1_DAG_3,
                            SCENARIO_1_DAG_SINGLETON_1
                        ),
                        maximalMinionsCount = Int.MAX_VALUE
                    ),
                    FactoryScenarioAssignment(
                        SCENARIO_2, listOf(
                            SCENARIO_2_DAG_2, SCENARIO_2_DAG_3
                        ),
                        maximalMinionsCount = Int.MAX_VALUE
                    )
                ),
                factory1
            )
            // Factory 2 has DAG-3, DAG-4 and DAG-5 for scenario 1, DAG-1, DAG-3 and DAG-4 for scenario 2.
            minionAssignmentKeeper.coInvokeInvisible<Unit>(
                "assignFactoryDags",
                CAMPAIGN,
                listOf(
                    FactoryScenarioAssignment(
                        SCENARIO_1, listOf(
                            SCENARIO_1_DAG_3,
                            SCENARIO_1_DAG_4,
                            SCENARIO_1_DAG_5,
                            SCENARIO_1_DAG_SINGLETON_2
                        ),
                        maximalMinionsCount = Int.MAX_VALUE
                    ),
                    FactoryScenarioAssignment(
                        SCENARIO_2, listOf(
                            SCENARIO_2_DAG_1, SCENARIO_2_DAG_3, SCENARIO_2_DAG_4
                        ),
                        maximalMinionsCount = Int.MAX_VALUE
                    )
                ),
                factory2
            )
            val assignedFactory1Scenario1Job = async {
                minionAssignmentKeeper.coInvokeInvisible<Map<MinionId, Collection<DirectedAcyclicGraphName>>>(
                    "assign",
                    CAMPAIGN,
                    SCENARIO_1,
                    factory1,
                    "the-factory-1-channel",
                    Int.MAX_VALUE
                )
            }
            val assignedFactory2Scenario1Job = async {
                minionAssignmentKeeper.coInvokeInvisible<Map<MinionId, Collection<DirectedAcyclicGraphName>>>(
                    "assign",
                    CAMPAIGN,
                    SCENARIO_1,
                    factory2,
                    "the-factory-2-channel",
                    Int.MAX_VALUE
                )
            }

            val assignedFactory1Scenario2Job = async {
                minionAssignmentKeeper.coInvokeInvisible<Map<MinionId, Collection<DirectedAcyclicGraphName>>>(
                    "assign",
                    CAMPAIGN,
                    SCENARIO_2,
                    factory1,
                    "the-factory-1-channel",
                    Int.MAX_VALUE
                )
            }
            val assignedFactory2Scenario2Job = async {
                minionAssignmentKeeper.coInvokeInvisible<Map<MinionId, Collection<DirectedAcyclicGraphName>>>(
                    "assign",
                    CAMPAIGN,
                    SCENARIO_2,
                    factory2,
                    "the-factory-2-channel",
                    Int.MAX_VALUE
                )
            }

            val assignedFactory1Scenario1 = assignedFactory1Scenario1Job.await()
            val assignedFactory2Scenario1 = assignedFactory2Scenario1Job.await()
            val assignedFactory1Scenario2 = assignedFactory1Scenario2Job.await()
            val assignedFactory2Scenario2 = assignedFactory2Scenario2Job.await()

            // then
            verify(exactly = 2) { localAssignmentStore.reset() }

            // Verifies that all the DAGs are assigned only once.
            val allAssignments = mutableMapOf<String, MutableSet<String>>()
            listOf(
                assignedFactory1Scenario1,
                assignedFactory1Scenario2,
                assignedFactory2Scenario1,
                assignedFactory2Scenario2
            ).forEach { assignments ->
                assignments.forEach { (minion, dags) ->
                    val allDags = allAssignments.computeIfAbsent(minion) { mutableSetOf() }
                    assertThat(
                        dags.intersect(allDags).isEmpty(),
                        "The DAGs $dags of minion $minion are assigned once"
                    ).isTrue()
                    allDags += dags
                }
            }

            // Verifies the assignments are complete.
            MINIONS_SCENARIO_1.forEach { minionId ->
                assertThat(allAssignments[minionId], "DAGS of minion $minionId").isNotNull()
                    .containsOnly(
                        SCENARIO_1_DAG_1,
                        SCENARIO_1_DAG_2,
                        SCENARIO_1_DAG_3,
                        SCENARIO_1_DAG_4,
                        SCENARIO_1_DAG_5
                    )
            }
            assertThat(allAssignments[MINIONS_SINGLETON_1], "DAGS of minion ${MINIONS_SINGLETON_1}").isNotNull()
                .containsOnly(SCENARIO_1_DAG_SINGLETON_1)
            assertThat(allAssignments[MINIONS_SINGLETON_2], "DAGS of minion ${MINIONS_SINGLETON_2}").isNotNull()
                .containsOnly(SCENARIO_1_DAG_SINGLETON_2)
            MINIONS_SCENARIO_2.forEach { minionId ->
                assertThat(allAssignments[minionId], "DAGS of minion $minionId").isNotNull()
                    .containsOnly(SCENARIO_2_DAG_1, SCENARIO_2_DAG_2, SCENARIO_2_DAG_3, SCENARIO_2_DAG_4)
            }

            verifyOnce {
                localAssignmentStore.save(SCENARIO_1, refEq(assignedFactory1Scenario1))
                localAssignmentStore.save(SCENARIO_1, refEq(assignedFactory2Scenario1))
                localAssignmentStore.save(SCENARIO_2, refEq(assignedFactory1Scenario2))
                localAssignmentStore.save(SCENARIO_2, refEq(assignedFactory2Scenario2))
            }
            excludeRecords { localAssignmentStore.hashCode() }
            confirmVerified(localAssignmentStore)

            // when
            val assignmentScenario1 = minionAssignmentKeeper.getFactoriesChannels(
                CAMPAIGN, SCENARIO_1,
                MINIONS_SCENARIO_1 + MINIONS_SINGLETON_1 + MINIONS_SINGLETON_2,
                listOf(
                    SCENARIO_1_DAG_1,
                    SCENARIO_1_DAG_2,
                    SCENARIO_1_DAG_3,
                    SCENARIO_1_DAG_4,
                    SCENARIO_1_DAG_5,
                    SCENARIO_1_DAG_SINGLETON_1,
                    SCENARIO_1_DAG_SINGLETON_2
                )
            )

            // then
            assignedFactory1Scenario1.forEach { (minion, dags) ->
                dags.forEach { dag ->
                    assertThat(assignmentScenario1.get(minion, dag)).isEqualTo("the-factory-1-channel")
                }
            }
            assignedFactory2Scenario1.forEach { (minion, dags) ->
                dags.forEach { dag ->
                    assertThat(assignmentScenario1.get(minion, dag)).isEqualTo("the-factory-2-channel")
                }
            }
        }

    @Test
    @Timeout(10)
    @Order(2)
    internal fun `should schedule the minions underload of all factories and throw a failure if not all minions are scheduled`(
        minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper
    ) = testDispatcherProvider.run {

        val assertionError = assertThrows<AssertionError> {
            minionAssignmentKeeper.schedule(
                CAMPAIGN,
                SCENARIO_1, listOf(
                    MinionsStartingLine(1, 123),
                )
            )
        }

        // then
        assertThat(assertionError.message).isEqualTo("999 minions could not be scheduled")
    }

    @Test
    @Timeout(10)
    @Order(3)
    internal fun `should schedule the minions underload of all factories`(minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper) =
        testDispatcherProvider.run {
            // when
            minionAssignmentKeeper.schedule(
                CAMPAIGN,
                SCENARIO_1, listOf(
                    MinionsStartingLine(60, 123),
                    MinionsStartingLine(50, 456),
                    MinionsStartingLine(15, 456), // Duplicate the offset to check the consistency.
                    MinionsStartingLine(
                        MINIONS_COUNT_IN_EACH_SCENARIO,
                        789
                    ),
                )
            )
            minionAssignmentKeeper.schedule(
                CAMPAIGN,
                SCENARIO_2, listOf(
                    MinionsStartingLine(50, 2123),
                    MinionsStartingLine(90, 2456),
                    MinionsStartingLine(25, 2456), // Duplicate the offset to check the consistency.
                    MinionsStartingLine(
                        MINIONS_COUNT_IN_EACH_SCENARIO,
                        2789
                    ),
                )
            )

            // then
            val scheduleForScenario1 = mutableMapOf<Long, MutableCollection<MinionId>>()
            minionAssignmentKeeper.readSchedulePlan(
                CAMPAIGN,
                SCENARIO_1, "the-factory-1-channel"
            )
                .forEach { (offset, minions) ->
                    scheduleForScenario1.computeIfAbsent(offset) { mutableSetOf() } += minions
                }
            minionAssignmentKeeper.readSchedulePlan(
                CAMPAIGN,
                SCENARIO_1, "the-factory-2-channel"
            )
                .forEach { (offset, minions) ->
                    scheduleForScenario1.computeIfAbsent(offset) { mutableSetOf() } += minions
                }
            assertThat(scheduleForScenario1).all {
                hasSize(3)
                key(123).hasSize(60)
                key(456).hasSize(65)
                key(789).hasSize(MINIONS_COUNT_IN_EACH_SCENARIO - 60 - 65)
            }


            val scheduleForScenario2 = mutableMapOf<Long, MutableCollection<MinionId>>()
            minionAssignmentKeeper.readSchedulePlan(
                CAMPAIGN,
                SCENARIO_2, "the-factory-1-channel"
            )
                .forEach { (offset, minions) ->
                    scheduleForScenario2.computeIfAbsent(offset) { mutableSetOf() } += minions
                }
            minionAssignmentKeeper.readSchedulePlan(
                CAMPAIGN,
                SCENARIO_2, "the-factory-2-channel"
            )
                .forEach { (offset, minions) ->
                    scheduleForScenario2.computeIfAbsent(offset) { mutableSetOf() } += minions
                }
            assertThat(scheduleForScenario2).all {
                hasSize(3)
                key(2123).hasSize(50)
                key(2456).hasSize(115)
                key(2789).hasSize(MINIONS_COUNT_IN_EACH_SCENARIO - 115 - 50)
            }
        }

    @Test
    @Timeout(10)
    @Order(4)
    internal fun `should not mark anything complete when not all the DAGS for all the minions are complete`(
        minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        MINIONS_SCENARIO_1.forEach { minion ->
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    campaignKey = CAMPAIGN,
                    scenarioName = SCENARIO_1,
                    minionId = minion,
                    dagIds = listOf(SCENARIO_1_DAG_1, SCENARIO_1_DAG_2, SCENARIO_1_DAG_3, SCENARIO_1_DAG_4),
                    mightRestart = false
                )
            ).all {
                prop(CampaignCompletionState::minionComplete).isFalse()
                prop(CampaignCompletionState::scenarioComplete).isFalse()
                prop(CampaignCompletionState::campaignComplete).isFalse()
            }
        }

        MINIONS_SCENARIO_2.forEach { minion ->
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    campaignKey = CAMPAIGN,
                    scenarioName = SCENARIO_2,
                    minionId = minion,
                    dagIds = listOf(SCENARIO_2_DAG_1, SCENARIO_2_DAG_2, SCENARIO_2_DAG_3),
                    mightRestart = false
                )
            ).all {
                prop(CampaignCompletionState::minionComplete).isFalse()
                prop(CampaignCompletionState::scenarioComplete).isFalse()
                prop(CampaignCompletionState::campaignComplete).isFalse()
            }
        }
    }

    @Test
    @Timeout(10)
    @Order(5)
    internal fun `should mark the minion complete when all the DAGS for all but 1 minion are complete`(
        minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        MINIONS_SCENARIO_1.subList(0, MINIONS_COUNT_IN_EACH_SCENARIO - 1).forEach { minion ->
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    campaignKey = CAMPAIGN,
                    scenarioName = SCENARIO_1,
                    minionId = minion,
                    dagIds = listOf(SCENARIO_1_DAG_5),
                    mightRestart = false
                )
            ).all {
                prop(CampaignCompletionState::minionComplete).isTrue()
                prop(CampaignCompletionState::scenarioComplete).isFalse()
                prop(CampaignCompletionState::campaignComplete).isFalse()
            }
        }

        MINIONS_SCENARIO_2.subList(0, MINIONS_COUNT_IN_EACH_SCENARIO - 1).forEach { minion ->
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    campaignKey = CAMPAIGN,
                    scenarioName = SCENARIO_2,
                    minionId = minion,
                    dagIds = listOf(SCENARIO_2_DAG_4),
                    mightRestart = false
                )
            ).all {
                prop(CampaignCompletionState::minionComplete).isTrue()
                prop(CampaignCompletionState::scenarioComplete).isFalse()
                prop(CampaignCompletionState::campaignComplete).isFalse()
            }
        }
    }

    @Test
    @Timeout(10)
    @Order(6)
    internal fun `should not complete the scenario when a singleton minion of a scenario is completed but a minion under load still runs`(
        minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        assertThat(
            minionAssignmentKeeper.executionComplete(
                campaignKey = CAMPAIGN,
                scenarioName = SCENARIO_1,
                minionId = MINIONS_SINGLETON_1,
                dagIds = listOf(SCENARIO_1_DAG_SINGLETON_1),
                mightRestart = false
            ),
            "Scenario ${SCENARIO_1} should not be complete"
        ).all {
            prop(CampaignCompletionState::minionComplete).isTrue()
            prop(CampaignCompletionState::scenarioComplete).isFalse()
            prop(CampaignCompletionState::campaignComplete).isFalse()
        }
    }

    @Test
    @Timeout(10)
    @Order(7)
    internal fun `should not complete the scenario when the latest minion of a scenario is completed but has to restart`(
        minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper,
        hashCoroutinesCommands: RedisHashCoroutinesCommands<String, String>
    ) = testDispatcherProvider.run {
        val minionId = MINIONS_SCENARIO_1.last()
        assertThat(
            minionAssignmentKeeper.executionComplete(
                campaignKey = CAMPAIGN,
                scenarioName = SCENARIO_1,
                minionId = minionId,
                dagIds = listOf(SCENARIO_1_DAG_5),
                mightRestart = true
            )
        ).all {
            prop(CampaignCompletionState::minionComplete).isTrue()
            prop(CampaignCompletionState::scenarioComplete).isFalse()
            prop(CampaignCompletionState::campaignComplete).isFalse()
        }

        // Checks that the number of remaining DAGs is properly reset without affecting the originally scheduled count of DAGs.
        val dagsForMinion =
            hashCoroutinesCommands.hgetall("{$CAMPAIGN}-assignment:$SCENARIO_1:minion:assigned-dags:${minionId}")
                .map { it.key to it.value }.toList().toMap()
        assertThat(dagsForMinion["remaining-dags"]).isNotNull().all {
            prop(String::toIntOrNull).isNotNull().isEqualTo(5)
            isEqualTo(dagsForMinion["scheduled-dags"])
        }
    }

    @Test
    @Timeout(10)
    @Order(8)
    internal fun `should complete the scenario when the latest minion of a scenario is completed even if a singleton still runs but other scenarios still run`(
        minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper,
        hashCoroutinesCommands: RedisHashCoroutinesCommands<String, String>
    ) = testDispatcherProvider.run {
        assertThat(
            minionAssignmentKeeper.executionComplete(
                campaignKey = CAMPAIGN,
                scenarioName = SCENARIO_1,
                minionId = MINIONS_SCENARIO_1.last(),
                dagIds = listOf(
                    SCENARIO_1_DAG_1,
                    SCENARIO_1_DAG_2,
                    SCENARIO_1_DAG_3,
                    SCENARIO_1_DAG_4
                ),
                mightRestart = true
            )
        ).all {
            prop(CampaignCompletionState::minionComplete).isFalse()
            prop(CampaignCompletionState::scenarioComplete).isFalse()
            prop(CampaignCompletionState::campaignComplete).isFalse()
        }

        assertThat(
            minionAssignmentKeeper.executionComplete(
                campaignKey = CAMPAIGN,
                scenarioName = SCENARIO_1,
                minionId = MINIONS_SCENARIO_1.last(),
                dagIds = listOf(SCENARIO_1_DAG_5),
                mightRestart = false
            )
        ).all {
            prop(CampaignCompletionState::minionComplete).isTrue()
            prop(CampaignCompletionState::scenarioComplete).isTrue()
            prop(CampaignCompletionState::campaignComplete).isFalse()
        }
    }

    @Test
    @Timeout(10)
    @Order(9)
    internal fun `should complete the campaign when the latest minion of the latest scenario completes its latest DAG`(
        minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        assertThat(
            minionAssignmentKeeper.executionComplete(
                campaignKey = CAMPAIGN,
                scenarioName = SCENARIO_2,
                minionId = MINIONS_SCENARIO_2.last(),
                dagIds = listOf(SCENARIO_2_DAG_4),
                mightRestart = false
            )
        ).all {
            prop(CampaignCompletionState::minionComplete).isTrue()
            prop(CampaignCompletionState::scenarioComplete).isTrue()
            prop(CampaignCompletionState::campaignComplete).isTrue()
        }
    }

    @Test
    @MicronautTest(startApplication = false)
    @PropertySource(
        Property(name = "factory.assignment.timeout", value = "1ms")
    )
    @Timeout(2)
    @Order(-1)
    internal fun `should assign until the timeout`(minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper) =
        testDispatcherProvider.run {
            // given
            every { scenarioRegistry.get("scenario")!!.dags } returns listOf(
                mockk {
                    every { name } returns "dag-1"
                    every { isRoot } returns true
                    every { isUnderLoad } returns true
                }
            )
            minionAssignmentKeeper.maxMinionsCountsByScenario().apply {
                clear()
                this["scenario"] = 1543
            }

            // when
            assertThrows<TimeoutCancellationException> {
                minionAssignmentKeeper.assign("campaign", "scenario")
            }
        }

    @Test
    @Timeout(10)
    @Order(Order.DEFAULT)
    @MicronautTest(startApplication = false)
    @PropertySource(
        Property(name = "factory.assignment.timeout", value = "2s"),
        Property(name = "factoryConfiguration.assignment.evaluation-batch-size", value = "61")
    )
    internal fun `should assign allowed count of minions`(minionAssignmentKeeper: RedisDistributedMinionAssignmentKeeper) =
        testDispatcherProvider.run {
            // given
            val campaign = "the-campaign-${(100 * Math.random()).toInt()}"
            minionAssignmentKeeper.registerMinionsToAssign(
                campaign,
                SCENARIO_1,
                listOf(SCENARIO_1_DAG_SINGLETON_1),
                (1..350).map { "minion-singleton-$it" },
                false
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                campaign,
                SCENARIO_1,
                listOf(SCENARIO_1_DAG_1, SCENARIO_1_DAG_2, SCENARIO_1_DAG_3, SCENARIO_1_DAG_4, SCENARIO_1_DAG_5),
                (1..350).map { "minion-$it" },
                true
            )
            minionAssignmentKeeper.completeUnassignedMinionsRegistration(campaign, SCENARIO_1)

            // when
            minionAssignmentKeeper.assignFactoryDags(
                campaign, listOf(
                    FactoryScenarioAssignment(
                        SCENARIO_1,
                        listOf(
                            SCENARIO_1_DAG_SINGLETON_1,
                            SCENARIO_1_DAG_1,
                            SCENARIO_1_DAG_2,
                            SCENARIO_1_DAG_3,
                            SCENARIO_1_DAG_4,
                            SCENARIO_1_DAG_5
                        ),
                        337
                    )
                )
            )
            val assignedMinions = minionAssignmentKeeper.assign(campaign, SCENARIO_1)

            // then
            assertThat(assignedMinions).all {
                prop(Map<*, *>::size).isGreaterThan(337) // All the minions, including the singletons.

                transform("Minions under load") { assignedMinions.keys.filterNot { it.contains("-singleton-") } }
                    .hasSize(337)
            }
        }


    private companion object {

        const val CAMPAIGN = "the-campaign"
        const val SCENARIO_1 = "the-scenario-1"
        const val SCENARIO_2 = "the-scenario-2"

        const val SCENARIO_1_DAG_1 = "the-dag-1-of-scenario-1"
        const val SCENARIO_1_DAG_2 = "the-dag-2-of-scenario-1"
        const val SCENARIO_1_DAG_3 = "the-dag-3-of-scenario-1"
        const val SCENARIO_1_DAG_4 = "the-dag-4-of-scenario-1"
        const val SCENARIO_1_DAG_5 = "the-dag-5-of-scenario-1"

        const val SCENARIO_2_DAG_1 = "the-dag-1-of-scenario-2"
        const val SCENARIO_2_DAG_2 = "the-dag-2-of-scenario-2"
        const val SCENARIO_2_DAG_3 = "the-dag-3-of-scenario-2"
        const val SCENARIO_2_DAG_4 = "the-dag-4-of-scenario-2"

        const val MINIONS_COUNT_IN_EACH_SCENARIO = 1000

        const val SCENARIO_1_DAG_SINGLETON_1 = "dag-singleton-1"
        const val MINIONS_SINGLETON_1 = "minion-singleton-1"

        const val SCENARIO_1_DAG_SINGLETON_2 = "dag-singleton-2"
        const val MINIONS_SINGLETON_2 = "minion-singleton-2"

        val MINIONS_SCENARIO_1 = (1..MINIONS_COUNT_IN_EACH_SCENARIO).map { "minion-$it" }

        val MINIONS_SCENARIO_2 =
            (MINIONS_COUNT_IN_EACH_SCENARIO + 1..2 * MINIONS_COUNT_IN_EACH_SCENARIO).map { "minion-$it" }

    }
}