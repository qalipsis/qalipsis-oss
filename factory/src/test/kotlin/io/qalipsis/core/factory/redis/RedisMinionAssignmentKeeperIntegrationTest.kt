package io.qalipsis.core.factory.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.orchestration.CampaignCompletionState
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import org.junit.jupiter.api.AfterAll
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
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.FACTORY])
internal class RedisMinionAssignmentKeeperIntegrationTest : AbstractRedisIntegrationTest() {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @MockBean(EventsLogger::class)
    fun eventsLogger() = eventsLogger

    @MockBean(LocalAssignmentStore::class)
    fun localAssignmentStore() = localAssignmentStore

    @AfterAll
    internal fun tearDownAll() {
        connection.sync().flushdb()
    }

    @Test
    @Timeout(10)
    @Order(1)
    @MicronautTest
    @PropertySource(Property(name = "factory.assignment.timeout", value = "2s"))
    internal fun `should assign a lot of minions only for the DAGs supported by the factory`(minionAssignmentKeeper: RedisMinionAssignmentKeeper) =
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
                mapOf(
                    SCENARIO_1 to listOf(
                        SCENARIO_1_DAG_1,
                        SCENARIO_1_DAG_2,
                        SCENARIO_1_DAG_3,
                        SCENARIO_1_DAG_SINGLETON_1
                    ),
                    SCENARIO_2 to listOf(SCENARIO_2_DAG_2, SCENARIO_2_DAG_3)
                ),
                factory1
            )
            // Factory 2 has DAG-3, DAG-4 and DAG-5 for scenario 1, DAG-1, DAG-3 and DAG-4 for scenario 2.
            minionAssignmentKeeper.coInvokeInvisible<Unit>(
                "assignFactoryDags",
                CAMPAIGN,
                mapOf(
                    SCENARIO_1 to listOf(
                        SCENARIO_1_DAG_3,
                        SCENARIO_1_DAG_4,
                        SCENARIO_1_DAG_5,
                        SCENARIO_1_DAG_SINGLETON_2
                    ),
                    SCENARIO_2 to listOf(SCENARIO_2_DAG_1, SCENARIO_2_DAG_3, SCENARIO_2_DAG_4)
                ),
                factory2
            )
            val assignedFactory1Scenario1Job = this.async {
                minionAssignmentKeeper.coInvokeInvisible<Map<MinionId, Collection<DirectedAcyclicGraphId>>>(
                    "assign",
                    CAMPAIGN,
                    SCENARIO_1,
                    factory1,
                    "the-factory-1-channel"
                )
            }
            val assignedFactory2Scenario1Job = this.async {
                minionAssignmentKeeper.coInvokeInvisible<Map<MinionId, Collection<DirectedAcyclicGraphId>>>(
                    "assign",
                    CAMPAIGN,
                    SCENARIO_1,
                    factory2,
                    "the-factory-2-channel"
                )
            }

            val assignedFactory1Scenario2Job = this.async {
                minionAssignmentKeeper.coInvokeInvisible<Map<MinionId, Collection<DirectedAcyclicGraphId>>>(
                    "assign",
                    CAMPAIGN,
                    SCENARIO_2,
                    factory1,
                    "the-factory-1-channel"
                )
            }
            val assignedFactory2Scenario2Job = this.async {
                minionAssignmentKeeper.coInvokeInvisible<Map<MinionId, Collection<DirectedAcyclicGraphId>>>(
                    "assign",
                    CAMPAIGN,
                    SCENARIO_2,
                    factory2,
                    "the-factory-2-channel"
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
    internal fun `should not mark anything complete when not all the DAGS for all the minions are complete`(
        minionAssignmentKeeper: RedisMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        MINIONS_SCENARIO_1.forEach { minion ->
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_1,
                    minion,
                    listOf(SCENARIO_1_DAG_1, SCENARIO_1_DAG_2, SCENARIO_1_DAG_3, SCENARIO_1_DAG_4)
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
                    CAMPAIGN,
                    SCENARIO_2,
                    minion,
                    listOf(SCENARIO_2_DAG_1, SCENARIO_2_DAG_2, SCENARIO_2_DAG_3)
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
    @Order(3)
    internal fun `should mark the minion complete when all the DAGS for all but 1 minion are complete`(
        minionAssignmentKeeper: RedisMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        MINIONS_SCENARIO_1.subList(0, MINIONS_COUNT_IN_EACH_SCENARIO - 1).forEach { minion ->
            assertThat(
                minionAssignmentKeeper.executionComplete(
                    CAMPAIGN,
                    SCENARIO_1,
                    minion,
                    listOf(SCENARIO_1_DAG_5)
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
                    CAMPAIGN,
                    SCENARIO_2,
                    minion,
                    listOf(SCENARIO_2_DAG_4)
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
    @Order(4)
    internal fun `should not complete the scenario when a singleton minion of a scenario is completed but a minion under load still runs`(
        minionAssignmentKeeper: RedisMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        assertThat(
            minionAssignmentKeeper.executionComplete(
                CAMPAIGN,
                SCENARIO_1,
                MINIONS_SINGLETON_1,
                listOf(SCENARIO_1_DAG_SINGLETON_1)
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
    @Order(5)
    internal fun `should complete the scenario when the latest minion of a scenario is completed even if a singleton still runs but other scenarios still run`(
        minionAssignmentKeeper: RedisMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        assertThat(
            minionAssignmentKeeper.executionComplete(
                CAMPAIGN,
                SCENARIO_1,
                MINIONS_SCENARIO_1.last(),
                listOf(SCENARIO_1_DAG_5)
            )
        ).all {
            prop(CampaignCompletionState::minionComplete).isTrue()
            prop(CampaignCompletionState::scenarioComplete).isTrue()
            prop(CampaignCompletionState::campaignComplete).isFalse()
        }
    }

    @Test
    @Timeout(10)
    @Order(6)
    internal fun `should complete the campaign when the latest minion of the latest scenario completes its latest DAG`(
        minionAssignmentKeeper: RedisMinionAssignmentKeeper
    ) = testDispatcherProvider.run {
        assertThat(
            minionAssignmentKeeper.executionComplete(
                CAMPAIGN,
                SCENARIO_2,
                MINIONS_SCENARIO_2.last(),
                listOf(SCENARIO_2_DAG_4)
            )
        ).all {
            prop(CampaignCompletionState::minionComplete).isTrue()
            prop(CampaignCompletionState::scenarioComplete).isTrue()
            prop(CampaignCompletionState::campaignComplete).isTrue()
        }
    }

    @Test
    @MicronautTest
    @PropertySource(
        Property(name = "factory.assignment.timeout", value = "1ms")
    )
    @Timeout(2)
    @Order(-1)
    internal fun `should assign until the timeout`(minionAssignmentKeeper: RedisMinionAssignmentKeeper) =
        testDispatcherProvider.run {
            // when
            assertThrows<TimeoutCancellationException> {
                minionAssignmentKeeper.assign("campaign", "scenario")
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