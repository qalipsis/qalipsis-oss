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

package io.qalipsis.core.factory.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.qalipsis.api.executionprofile.MinionsStartingLine
import io.qalipsis.core.factory.inmemory.catadioptre.remainingDagsCountByMinions
import io.qalipsis.core.factory.inmemory.catadioptre.scheduledDagsCountByMinions
import io.qalipsis.core.factory.orchestration.CampaignCompletionState
import io.qalipsis.core.factory.orchestration.LocalAssignmentStoreImpl
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.async
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger

@WithMockk
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class InMemoryMinionAssignmentKeeperTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @SpyK
    private var localAssignmentStore = LocalAssignmentStoreImpl(relaxedMockk())

    @InjectMockKs
    private lateinit var minionAssignmentKeeper: InMemoryMinionAssignmentKeeper

    @Test
    @Timeout(5)
    @Order(1)
    internal fun `should assign a lot of minions only for the DAGs supported by the factory`() =
        testDispatcherProvider.run {
            // given
            minionAssignmentKeeper.registerMinionsToAssign(
                CAMPAIGN,
                SCENARIO_1,
                listOf(SCENARIO_1_DAG_1, SCENARIO_1_DAG_2, SCENARIO_1_DAG_3, SCENARIO_1_DAG_4, SCENARIO_1_DAG_5),
                MINIONS_SCENARIO_1,
                true
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
                MINIONS_SCENARIO_2,
                true
            )
            minionAssignmentKeeper.completeUnassignedMinionsRegistration(CAMPAIGN, SCENARIO_1)
            minionAssignmentKeeper.completeUnassignedMinionsRegistration(CAMPAIGN, SCENARIO_2)

            // when
            val minionsScenario1UnderLoad = minionAssignmentKeeper.getIdsOfMinionsUnderLoad(CAMPAIGN, SCENARIO_1)
            val minionsScenario2UnderLoad = minionAssignmentKeeper.getIdsOfMinionsUnderLoad(
                CAMPAIGN, SCENARIO_2
            )

            // then
            assertThat(minionsScenario1UnderLoad.sorted()).containsExactly(*MINIONS_SCENARIO_1.sorted().toTypedArray())
            assertThat(minionsScenario2UnderLoad.sorted()).containsExactly(*MINIONS_SCENARIO_2.sorted().toTypedArray())

            // when
            val assignedScenario1Job = this.async {
                minionAssignmentKeeper.assign(CAMPAIGN, SCENARIO_1)
            }
            val assignedScenario2Job = this.async {
                minionAssignmentKeeper.assign(CAMPAIGN, SCENARIO_2)
            }

            val assignedScenario1 = assignedScenario1Job.await()
            val assignedScenario2 = assignedScenario2Job.await()

            // then

            // Verifies that all the DAGs are assigned only once.
            val allAssignments = mutableMapOf<String, MutableSet<String>>()
            listOf(
                assignedScenario1,
                assignedScenario2,
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
            assertThat(allAssignments[MINIONS_SINGLETON_1], "DAGS of minion $MINIONS_SINGLETON_1").isNotNull()
                .containsOnly(
                    SCENARIO_1_DAG_SINGLETON_1
                )
            assertThat(allAssignments[MINIONS_SINGLETON_2], "DAGS of minion $MINIONS_SINGLETON_2").isNotNull()
                .containsOnly(
                    SCENARIO_1_DAG_SINGLETON_2
                )
            MINIONS_SCENARIO_2.forEach { minionId ->
                assertThat(allAssignments[minionId], "DAGS of minion $minionId").isNotNull()
                    .containsOnly(SCENARIO_2_DAG_1, SCENARIO_2_DAG_2, SCENARIO_2_DAG_3, SCENARIO_2_DAG_4)
            }
        }


    @Test
    @Timeout(10)
    @Order(2)
    internal fun `should schedule the minions underload of all factories and throw a failure if not all minions are scheduled`() =
        testDispatcherProvider.run {

            val assertionError = assertThrows<AssertionError> {
                minionAssignmentKeeper.schedule(
                    CAMPAIGN,
                    SCENARIO_1,
                    listOf(
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
    internal fun `should schedule the minions underload of all factories`() =
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
            val scheduleForScenario1 = minionAssignmentKeeper.readSchedulePlan(CAMPAIGN, SCENARIO_1)
            assertThat(scheduleForScenario1).all {
                hasSize(3)
                key(123).hasSize(60)
                key(456).hasSize(65)
                key(789).hasSize(MINIONS_COUNT_IN_EACH_SCENARIO - 60 - 65)
            }
            val scheduleForScenario2 = minionAssignmentKeeper.readSchedulePlan(CAMPAIGN, SCENARIO_2)
            assertThat(scheduleForScenario2).all {
                hasSize(3)
                key(2123).hasSize(50)
                key(2456).hasSize(115)
                key(2789).hasSize(MINIONS_COUNT_IN_EACH_SCENARIO - 115 - 50)
            }
        }

    @Test
    @Timeout(5)
    @Order(4)
    internal fun `should not mark anything complete when not all the DAGS for all the minions are complete`() =
        testDispatcherProvider.run {
            MINIONS_SCENARIO_1.forEach { minion ->
                assertThat(
                    minionAssignmentKeeper.executionComplete(
                        CAMPAIGN,
                        SCENARIO_1,
                        minion,
                        listOf(SCENARIO_1_DAG_1, SCENARIO_1_DAG_2, SCENARIO_1_DAG_3, SCENARIO_1_DAG_4),
                        false
                    ),
                    "Minion $minion for scenario $SCENARIO_1 should not be complete"
                ).all {
                    prop(CampaignCompletionState::minionComplete).isFalse()
                    prop(CampaignCompletionState::scenarioComplete).isFalse()
                    prop(CampaignCompletionState::campaignComplete).isFalse()
                }
            }

            MINIONS_SCENARIO_2.forEach { minion ->
                assertThat(
                    minionAssignmentKeeper.executionComplete(
                        CAMPAIGN,
                        SCENARIO_2,
                        minion,
                        listOf(SCENARIO_2_DAG_1, SCENARIO_2_DAG_2, SCENARIO_2_DAG_3),
                        false
                    ),
                    "Minion $minion for scenario $SCENARIO_2 should not be complete"
                ).all {
                    prop(CampaignCompletionState::minionComplete).isFalse()
                    prop(CampaignCompletionState::scenarioComplete).isFalse()
                    prop(CampaignCompletionState::campaignComplete).isFalse()
                }
            }
        }

    @Test
    @Timeout(5)
    @Order(5)
    internal fun `should mark the minion complete when all the DAGS for all but 1 minion are complete`() =
        testDispatcherProvider.run {
            MINIONS_SCENARIO_1.subList(0, MINIONS_COUNT_IN_EACH_SCENARIO - 1).forEach { minion ->
                assertThat(
                    minionAssignmentKeeper.executionComplete(
                        CAMPAIGN,
                        SCENARIO_1,
                        minion,
                        listOf(SCENARIO_1_DAG_5),
                        false
                    ),
                    "Minion $minion for scenario $SCENARIO_1 should be complete"
                ).all {
                    prop(CampaignCompletionState::minionComplete).isTrue()
                    prop(CampaignCompletionState::scenarioComplete).isFalse()
                    prop(CampaignCompletionState::campaignComplete).isFalse()
                }
            }

            MINIONS_SCENARIO_2.subList(0, MINIONS_COUNT_IN_EACH_SCENARIO - 1).forEach { minion ->
                assertThat(
                    minionAssignmentKeeper.executionComplete(
                        CAMPAIGN,
                        SCENARIO_2,
                        minion,
                        listOf(SCENARIO_2_DAG_4),
                        false
                    ),
                    "Minion $minion for scenario $SCENARIO_2 should be complete"
                ).all {
                    prop(CampaignCompletionState::minionComplete).isTrue()
                    prop(CampaignCompletionState::scenarioComplete).isFalse()
                    prop(CampaignCompletionState::campaignComplete).isFalse()
                }
            }
        }

    @Test
    @Timeout(5)
    @Order(6)
    internal fun `should not complete the scenario when a singleton minion of a scenario is completed but a minion under load still runs`() =
        testDispatcherProvider.run {
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_1,
                    MINIONS_SINGLETON_1,
                    listOf(SCENARIO_1_DAG_SINGLETON_1),
                    false
                ),
                "Scenario $SCENARIO_1 should not be complete"
            ).all {
                prop(CampaignCompletionState::minionComplete).isTrue()
                prop(CampaignCompletionState::scenarioComplete).isFalse()
                prop(CampaignCompletionState::campaignComplete).isFalse()
            }
        }

    @Test
    @Timeout(5)
    @Order(7)
    internal fun `should not complete the scenario when the latest minion should restart`() =
        testDispatcherProvider.run {
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_1,
                    MINIONS_SCENARIO_1.last(),
                    listOf(SCENARIO_1_DAG_5),
                    true
                ),
                "Scenario $SCENARIO_1 should be complete"
            ).all {
                prop(CampaignCompletionState::minionComplete).isTrue()
                prop(CampaignCompletionState::scenarioComplete).isFalse()
                prop(CampaignCompletionState::campaignComplete).isFalse()
            }

            // Checks that the number of remaining DAGs is properly reset without affecting the originally scheduled count of DAGs.
            assertThat(minionAssignmentKeeper.scheduledDagsCountByMinions()[SCENARIO_1, MINIONS_SCENARIO_1.last()]).isNotNull()
                .prop(AtomicInteger::get).isEqualTo(5)
            assertThat(minionAssignmentKeeper.remainingDagsCountByMinions()[SCENARIO_1, MINIONS_SCENARIO_1.last()]).isNotNull()
                .all {
                    isNotSameAs(minionAssignmentKeeper.scheduledDagsCountByMinions()[SCENARIO_1, MINIONS_SCENARIO_1.last()])
                    prop(AtomicInteger::get).isEqualTo(5)
                }
        }

    @Test
    @Timeout(5)
    @Order(8)
    internal fun `should complete the scenario when the latest minion of a scenario is completed even if a singleton still runs but other scenarios still run`() =
        testDispatcherProvider.run {
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_1,
                    MINIONS_SCENARIO_1.last(),
                    listOf(SCENARIO_1_DAG_1, SCENARIO_1_DAG_2, SCENARIO_1_DAG_3, SCENARIO_1_DAG_4),
                    true
                )
            ).all {
                prop(CampaignCompletionState::minionComplete).isFalse()
                prop(CampaignCompletionState::scenarioComplete).isFalse()
                prop(CampaignCompletionState::campaignComplete).isFalse()
            }

            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_1,
                    MINIONS_SCENARIO_1.last(),
                    listOf(SCENARIO_1_DAG_5),
                    false
                ),
                "Scenario $SCENARIO_1 should be complete"
            ).all {
                prop(CampaignCompletionState::minionComplete).isTrue()
                prop(CampaignCompletionState::scenarioComplete).isTrue()
                prop(CampaignCompletionState::campaignComplete).isFalse()
            }
        }

    @Test
    @Timeout(5)
    @Order(9)
    internal fun `should complete the campaign when the latest minion of the latest scenario completes its latest DAG`() =
        testDispatcherProvider.run {
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_2,
                    MINIONS_SCENARIO_2.last(),
                    listOf(SCENARIO_2_DAG_4),
                    false
                ),
                "Campaign should be complete"
            ).all {
                prop(CampaignCompletionState::minionComplete).isTrue()
                prop(CampaignCompletionState::scenarioComplete).isTrue()
                prop(CampaignCompletionState::campaignComplete).isTrue()
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