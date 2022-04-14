package io.qalipsis.core.factory.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
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
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class InMemoryMinionAssignmentKeeperTest {

    @JvmField
    @RegisterExtension
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
    @Timeout(5)
    @Order(2)
    internal fun `should not mark anything complete when not all the DAGS for all the minions are complete`() =
        testDispatcherProvider.run {
            MINIONS_SCENARIO_1.forEach { minion ->
                assertThat(
                    minionAssignmentKeeper.executionComplete(
                        CAMPAIGN,
                        SCENARIO_1,
                        minion,
                        listOf(SCENARIO_1_DAG_1, SCENARIO_1_DAG_2, SCENARIO_1_DAG_3, SCENARIO_1_DAG_4)
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
                        listOf(SCENARIO_2_DAG_1, SCENARIO_2_DAG_2, SCENARIO_2_DAG_3)
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
    @Order(3)
    internal fun `should mark the minion complete when all the DAGS for all but 1 minion are complete`() =
        testDispatcherProvider.run {
            MINIONS_SCENARIO_1.subList(0, MINIONS_COUNT_IN_EACH_SCENARIO - 1).forEach { minion ->
                assertThat(
                    minionAssignmentKeeper.executionComplete(
                        CAMPAIGN,
                        SCENARIO_1,
                        minion,
                        listOf(SCENARIO_1_DAG_5)
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
                        listOf(SCENARIO_2_DAG_4)
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
    @Order(4)
    internal fun `should not complete the scenario when a singleton minion of a scenario is completed but a minion under load still runs`() =
        testDispatcherProvider.run {
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_1,
                    MINIONS_SINGLETON_1,
                    listOf(SCENARIO_1_DAG_SINGLETON_1)
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
    @Order(5)
    internal fun `should complete the scenario when the latest minion of a scenario is completed even if a singleton still runs but other scenarios still run`() =
        testDispatcherProvider.run {
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_1,
                    MINIONS_SCENARIO_1.last(),
                    listOf(SCENARIO_1_DAG_5)
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
    @Order(6)
    internal fun `should complete the campaign when the latest minion of the latest scenario completes its latest DAG`() =
        testDispatcherProvider.run {
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_2,
                    MINIONS_SCENARIO_2.last(),
                    listOf(SCENARIO_2_DAG_4)
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