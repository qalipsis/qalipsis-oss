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

package io.qalipsis.core.head.redis.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import com.google.common.collect.ImmutableTable
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.AbortingState
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@ExperimentalLettuceCoroutinesApi
@WithMockk
@Timeout(4)
internal class RedisCampaignManagerTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var headChannel: HeadChannel

    @MockK
    private lateinit var factoryService: FactoryService

    @MockK
    private lateinit var assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver

    @RelaxedMockK
    private lateinit var campaignService: CampaignService

    @RelaxedMockK
    private lateinit var operations: CampaignRedisOperations

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @MockK
    private lateinit var headConfiguration: HeadConfiguration

    @MockK
    private lateinit var campaignExecutionContext: CampaignExecutionContext

    @Test
    internal fun `should accept the feedback only if it is a CampaignManagementFeedback`() {
        val campaignManager = redisCampaignManager(relaxedMockk())
        assertThat(
            campaignManager.accept(
                relaxedMockk(
                    "campaign-feedback",
                    CampaignManagementFeedback::class
                )
            )
        ).isTrue()
        assertThat(campaignManager.accept(relaxedMockk("non-campaign-feedback"))).isFalse()
    }

    @Test
    internal fun `should start a new campaign when all the scenarios are currently supported and release the unused factories`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(6272),
                    "scenario-2" to ScenarioRequest(12321)
                )
            )
            val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign")
            coEvery {
                campaignService.create(
                    "my-tenant",
                    "my-user",
                    refEq(campaign)
                )
            } returns runningCampaign
            val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            val scenario2 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-2" }
            val scenario3 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            coEvery { factoryService.getActiveScenarios(any(), setOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2, scenario3)
            val factory1 =
                relaxedMockk<Factory> { every { nodeId } returns "factory-1"; every { unicastChannel } returns "unicast-channel-1" }
            val factory2 = relaxedMockk<Factory> { every { nodeId } returns "factory-2" };
            val factory3 =
                relaxedMockk<Factory> { every { nodeId } returns "factory-3"; every { unicastChannel } returns "unicast-channel-3" }
            coEvery {
                factoryService.getAvailableFactoriesForScenarios("my-tenant", setOf("scenario-1", "scenario-2"))
            } returns listOf(factory1, factory2, factory3)
            coJustRun { factoryService.lockFactories(any(), any()) }

            val assignments = ImmutableTable.builder<NodeId, ScenarioName, FactoryScenarioAssignment>()
                .put("factory-1", "scenario-1", FactoryScenarioAssignment("scenario-1", listOf("dag-1", "dag-2")))
                .put("factory-1", "scenario-2", FactoryScenarioAssignment("scenario-2", listOf("dag-A", "dag-B"), 1762))
                .put(
                    "factory-3",
                    "scenario-2",
                    FactoryScenarioAssignment("scenario-2", listOf("dag-A", "dag-B", "dag-C"), 254)
                )
                .build()
            coEvery {
                assignmentResolver.resolveFactoriesAssignments(
                    refEq(runningCampaign),
                    listOf(factory1, factory2, factory3),
                    listOf(scenario1, scenario2)
                )
            } returns assignments
            coJustRun { campaignService.start(any(), any(), any(), any()) }
            coJustRun { campaignService.startScenario(any(), any(), any(), any()) }
            coJustRun { campaignReportStateKeeper.start(any(), any()) }
            coJustRun { factoryService.releaseFactories(any(), any()) }
            coJustRun { headChannel.subscribeFeedback(any()) }
            val countDown = SuspendedCountLatch(2)
            coEvery { headChannel.publishDirective(any()) } coAnswers { countDown.decrement() }

            // when
            val result = campaignManager.start("my-tenant", "my-user", campaign)
            // Wait for the latest directive to be sent.
            countDown.await()

            // then
            assertThat(result).isSameAs(runningCampaign)
            assertThat(runningCampaign.factories).all {
                hasSize(2)
                key("factory-1").all {
                    prop(FactoryConfiguration::unicastChannel).isEqualTo("unicast-channel-1")
                    prop(FactoryConfiguration::assignment).isEqualTo(
                        linkedMapOf(
                            "scenario-1" to FactoryScenarioAssignment("scenario-1", listOf("dag-1", "dag-2")),
                            "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-A", "dag-B"), 1762)
                        )
                    )
                }
                key("factory-3").all {
                    prop(FactoryConfiguration::unicastChannel).isEqualTo("unicast-channel-3")
                    prop(FactoryConfiguration::assignment).isEqualTo(
                        linkedMapOf(
                            "scenario-2" to FactoryScenarioAssignment(
                                "scenario-2",
                                listOf("dag-A", "dag-B", "dag-C"),
                                254
                            )
                        )
                    )
                }
            }

            val sentDirectives = mutableListOf<Directive>()
            coVerifyOrder {
                factoryService.getActiveScenarios("my-tenant", setOf("scenario-1", "scenario-2"))
                campaignService.create("my-tenant", "my-user", refEq(campaign))
                factoryService.getAvailableFactoriesForScenarios("my-tenant", setOf("scenario-1", "scenario-2"))
                factoryService.lockFactories(refEq(runningCampaign), listOf("factory-1", "factory-2", "factory-3"))
                assignmentResolver.resolveFactoriesAssignments(
                    refEq(runningCampaign),
                    listOf(factory1, factory2, factory3),
                    listOf(scenario1, scenario2)
                )
                campaignService.start("my-tenant", "my-campaign", any(), isNull())
                campaignService.startScenario("my-tenant", "my-campaign", "scenario-1", any())
                campaignReportStateKeeper.start("my-campaign", "scenario-1")
                campaignService.startScenario("my-tenant", "my-campaign", "scenario-2", any())
                campaignReportStateKeeper.start("my-campaign", "scenario-2")
                factoryService.releaseFactories(refEq(runningCampaign), listOf("factory-2"))
                headChannel.subscribeFeedback("feedbacks")
                headChannel.publishDirective(capture(sentDirectives))
                headChannel.publishDirective(capture(sentDirectives))
            }
            assertThat(sentDirectives).all {
                hasSize(2)
                any {
                    it.isInstanceOf(FactoryAssignmentDirective::class).all {
                        prop(FactoryAssignmentDirective::campaignKey).isEqualTo("my-campaign")
                        prop(FactoryAssignmentDirective::assignments).all {
                            hasSize(2)
                            any {
                                it.all {
                                    prop(FactoryScenarioAssignment::scenarioName).isEqualTo("scenario-1")
                                    prop(FactoryScenarioAssignment::dags).containsOnly("dag-1", "dag-2")
                                    prop(FactoryScenarioAssignment::maximalMinionCount).isEqualTo(Int.MAX_VALUE)
                                }
                            }
                            any {
                                it.all {
                                    prop(FactoryScenarioAssignment::scenarioName).isEqualTo("scenario-2")
                                    prop(FactoryScenarioAssignment::dags).containsOnly("dag-A", "dag-B")
                                    prop(FactoryScenarioAssignment::maximalMinionCount).isEqualTo(1762)
                                }
                            }
                        }
                        prop(FactoryAssignmentDirective::broadcastChannel).isEqualTo("directives-broadcast")
                        prop(FactoryAssignmentDirective::feedbackChannel).isEqualTo("feedbacks")
                        prop(FactoryAssignmentDirective::channel).isEqualTo("unicast-channel-1")
                    }
                }
                any {
                    it.isInstanceOf(FactoryAssignmentDirective::class).all {
                        prop(FactoryAssignmentDirective::campaignKey).isEqualTo("my-campaign")
                        prop(FactoryAssignmentDirective::assignments).all {
                            hasSize(1)
                            any {
                                it.all {
                                    prop(FactoryScenarioAssignment::scenarioName).isEqualTo("scenario-2")
                                    prop(FactoryScenarioAssignment::dags).containsOnly("dag-A", "dag-B", "dag-C")
                                    prop(FactoryScenarioAssignment::maximalMinionCount).isEqualTo(254)
                                }
                            }
                        }
                        prop(FactoryAssignmentDirective::broadcastChannel).isEqualTo("directives-broadcast")
                        prop(FactoryAssignmentDirective::feedbackChannel).isEqualTo("feedbacks")
                        prop(FactoryAssignmentDirective::channel).isEqualTo("unicast-channel-3")
                    }
                }
            }
        }

    @Test
    internal fun `should close the campaign after creation when an exception occurs`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf("scenario-1" to ScenarioRequest(6272))
            )
            val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign")
            coEvery {
                campaignService.create("my-tenant", "my-user", refEq(campaign))
            } returns runningCampaign
            coEvery { factoryService.getActiveScenarios("my-tenant", setOf("scenario-1")) } returns
                    listOf(relaxedMockk { every { name } returns "scenario-1" })
            coEvery {
                factoryService.getAvailableFactoriesForScenarios(
                    "my-tenant",
                    any()
                )
            } throws RuntimeException("Something wrong occurred")
            val latch = Latch(true)
            coEvery { campaignService.close(any(), any(), any()) } coAnswers { latch.release(); relaxedMockk() }

            // when
            val exception = assertThrows<RuntimeException> {
                campaignManager.start("my-tenant", "my-user", campaign)
            }

            // then
            assertThat(exception.message).isEqualTo("Something wrong occurred")
            coVerifyOrder {
                factoryService.getActiveScenarios("my-tenant", setOf("scenario-1"))
                campaignService.create("my-tenant", "my-user", refEq(campaign))
                factoryService.getAvailableFactoriesForScenarios("my-tenant", setOf("scenario-1"))
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED)
            }
            confirmVerified(assignmentResolver, factoryService, headChannel, campaignService)
        }

    @Test
    internal fun `should not start a new campaign when some scenarios are currently not supported`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = CampaignConfiguration(
                name = "my-campaign",
                scenarios = mapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk()),
            )
            val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            coEvery { factoryService.getActiveScenarios(any(), setOf("scenario-1", "scenario-2")) } returns listOf(
                scenario1
            )

            // when + then
            assertThrows<IllegalArgumentException> {
                campaignManager.start("my-tenant", "my-user", campaign)
            }
        }

    @Test
    internal fun `should return RedisFactoryAssignmentState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.FACTORY_DAGS_ASSIGNMENT_STATE)

            // when
            val state = campaignManager.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisFactoryAssignmentState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }

    @Test
    internal fun `should return RedisMinionsAssignmentState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.MINIONS_ASSIGNMENT_STATE)

            // when
            val state = campaignManager.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisMinionsAssignmentState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }

    @Test
    internal fun `should return RedisWarmupState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.WARMUP_STATE)

            // when
            val state = campaignManager.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisWarmupState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }

    @Test
    internal fun `should return RedisMinionsStartupState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.MINIONS_STARTUP_STATE)

            // when
            val state = campaignManager.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisMinionsStartupState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }

    @Test
    internal fun `should return RedisRunningState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.RUNNING_STATE)

            // when
            val state = campaignManager.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisRunningState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }

    @Test
    internal fun `should return RedisCompletionState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.COMPLETION_STATE)

            // when
            val state = campaignManager.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisCompletionState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }

    @Test
    internal fun `should return RedisFailureState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.FAILURE_STATE)

            // when
            val state = campaignManager.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }

    @Test
    internal fun `should return RedisAbortingState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignManager = redisCampaignManager(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.ABORTING_STATE)

            // when
            val state = campaignManager.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisAbortingState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }

    @Test
    internal fun `should abort hard a campaign`() = testDispatcherProvider.runTest {
        //given
        val campaignManager = redisCampaignManager(this)
        val campaign = RunningCampaign(
            tenant = "my-tenant",
            key = "first_campaign",
            scenarios = linkedMapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
        )
        campaign.message = "problem"
        campaign.broadcastChannel = "channel"
        coEvery {
            operations.getState("my-tenant", "first_campaign")
        } returns Pair(campaign, CampaignRedisState.RUNNING_STATE)

        // when
        campaignManager.abort("my-user", "my-tenant", "first_campaign", true)

        // then
        val sentDirectives = mutableListOf<Directive>()
        val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
        coVerifyOrder {
            campaignManager.get("my-tenant", "first_campaign")
            operations.getState("my-tenant", "first_campaign")
            campaignService.abort("my-tenant", "my-user", "first_campaign")
            campaignManager.set(capture(newState))
            headChannel.publishDirective(capture(sentDirectives))
        }
        assertThat(newState.captured).isInstanceOf(AbortingState::class).all {
            prop("context").isSameAs(campaignExecutionContext)
            prop("operations").isSameAs(operations)
            typedProp<Boolean>("initialized").isTrue()
            prop("abortConfiguration").all {
                typedProp<Boolean>("hard").isEqualTo(true)
            }
        }
        assertThat(sentDirectives).all {
            hasSize(1)
            any {
                it.isInstanceOf(CampaignAbortDirective::class).all {
                    prop(CampaignAbortDirective::campaignKey).isEqualTo("first_campaign")
                    prop(CampaignAbortDirective::channel).isEqualTo("channel")
                    prop(CampaignAbortDirective::abortRunningCampaign).all {
                        typedProp<Boolean>("hard").isEqualTo(true)
                    }
                    prop(CampaignAbortDirective::scenarioNames).all {
                        hasSize(2)
                        index(0).isEqualTo("scenario-1")
                        index(1).isEqualTo("scenario-2")
                    }
                }
            }
        }
    }

    @Test
    internal fun `should abort soft a campaign`() = testDispatcherProvider.runTest {
        //given
        val campaignManager = redisCampaignManager(this)
        val campaign = RunningCampaign(
            tenant = "my-tenant",
            key = "first_campaign",
            scenarios = linkedMapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
        )
        campaign.message = "problem"
        campaign.broadcastChannel = "channel"
        coEvery {
            operations.getState("my-tenant", "first_campaign")
        } returns Pair(campaign, CampaignRedisState.RUNNING_STATE)

        // when
        campaignManager.abort("my-user", "my-tenant", "first_campaign", false)

        // then
        val sentDirectives = mutableListOf<Directive>()
        val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
        coVerifyOrder {
            campaignManager.get("my-tenant", "first_campaign")
            operations.getState("my-tenant", "first_campaign")
            campaignService.abort("my-tenant", "my-user", "first_campaign")
            campaignManager.set(capture(newState))
            headChannel.publishDirective(capture(sentDirectives))
        }
        assertThat(newState.captured).isInstanceOf(AbortingState::class).all {
            prop("context").isSameAs(campaignExecutionContext)
            prop("operations").isSameAs(operations)
            typedProp<Boolean>("initialized").isTrue()
            prop("abortConfiguration").all {
                typedProp<Boolean>("hard").isEqualTo(false)
            }
        }
        assertThat(sentDirectives).all {
            hasSize(1)
            any {
                it.isInstanceOf(CampaignAbortDirective::class).all {
                    prop(CampaignAbortDirective::campaignKey).isEqualTo("first_campaign")
                    prop(CampaignAbortDirective::channel).isEqualTo("channel")
                    prop(CampaignAbortDirective::abortRunningCampaign).all {
                        typedProp<Boolean>("hard").isEqualTo(false)
                    }
                    prop(CampaignAbortDirective::scenarioNames).all {
                        hasSize(2)
                        index(0).isEqualTo("scenario-1")
                        index(1).isEqualTo("scenario-2")
                    }
                }
            }
        }
    }

    private fun redisCampaignManager(scope: CoroutineScope) =
        spyk(
            RedisCampaignManager(
                headChannel,
                factoryService,
                assignmentResolver,
                campaignService,
                campaignReportStateKeeper,
                headConfiguration,
                scope,
                campaignExecutionContext,
                operations
            ), recordPrivateCalls = true
        )
}