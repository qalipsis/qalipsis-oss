package io.qalipsis.core.head.redis.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
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
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.slot
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.FactoryConfiguration
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.FactoryAssignmentState
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@ExperimentalLettuceCoroutinesApi
@WithMockk
internal class RedisCampaignManagerTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var headChannel: HeadChannel

    @RelaxedMockK
    private lateinit var factoryService: FactoryService

    @RelaxedMockK
    private lateinit var assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver

    @RelaxedMockK
    private lateinit var campaignService: CampaignService

    @RelaxedMockK
    private lateinit var operations: CampaignRedisOperations

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @RelaxedMockK
    private lateinit var campaignExecutionContext: CampaignExecutionContext

    @InjectMockKs
    @SpyK(recordPrivateCalls = true)
    private lateinit var campaignManager: RedisCampaignManager

    @Test
    internal fun `should accept the feedback only if it is a CampaignManagementFeedback`() {
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
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                id = "my-campaign",
                scenarios = mapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
            )
            val scenario1 = relaxedMockk<ScenarioSummary> { every { id } returns "scenario-1" }
            val scenario2 = relaxedMockk<ScenarioSummary> { every { id } returns "scenario-2" }
            val scenario3 = relaxedMockk<ScenarioSummary> { every { id } returns "scenario-1" }
            coEvery { factoryService.getActiveScenarios(setOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2, scenario3)
            val factory1 =
                relaxedMockk<Factory> { every { nodeId } returns "factory-1"; every { unicastChannel } returns "unicast-channel-1" }
            val factory2 = relaxedMockk<Factory> { every { nodeId } returns "factory-2" };
            val factory3 =
                relaxedMockk<Factory> { every { nodeId } returns "factory-3"; every { unicastChannel } returns "unicast-channel-3" }
            coEvery { factoryService.getAvailableFactoriesForScenarios(setOf("scenario-1", "scenario-2")) } returns
                    listOf(factory1, factory2, factory3)
            val assignments = ImmutableTable.builder<NodeId, ScenarioId, Collection<DirectedAcyclicGraphId>>()
                .put("factory-1", "scenario-1", listOf("dag-1", "dag-2"))
                .put("factory-1", "scenario-2", listOf("dag-A", "dag-B"))
                .put("factory-3", "scenario-2", listOf("dag-A", "dag-B", "dag-C"))
                .build()
            coEvery {
                assignmentResolver.resolveFactoriesAssignments(
                    refEq(campaign),
                    listOf(factory1, factory2, factory3),
                    listOf(scenario1, scenario2)
                )
            } returns assignments

            // when
            campaignManager.start(campaign)

            // then
            assertThat(campaign.factories).all {
                hasSize(2)
                key("factory-1").all {
                    prop(FactoryConfiguration::unicastChannel).isEqualTo("unicast-channel-1")
                    prop(FactoryConfiguration::assignment).isEqualTo(
                        mutableMapOf(
                            "scenario-1" to listOf("dag-1", "dag-2"),
                            "scenario-2" to listOf("dag-A", "dag-B")
                        )
                    )
                }
                key("factory-3").all {
                    prop(FactoryConfiguration::unicastChannel).isEqualTo("unicast-channel-3")
                    prop(FactoryConfiguration::assignment).isEqualTo(
                        mutableMapOf(
                            "scenario-2" to listOf("dag-A", "dag-B", "dag-C")
                        )
                    )
                }
            }

            val sentDirectives = mutableListOf<Directive>()
            val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
            coVerifyOrder {
                factoryService.getActiveScenarios(setOf("scenario-1", "scenario-2"))
                factoryService.getAvailableFactoriesForScenarios(setOf("scenario-1", "scenario-2"))
                campaignService.save(refEq(campaign))
                factoryService.lockFactories(refEq(campaign), listOf("factory-1", "factory-2", "factory-3"))
                assignmentResolver.resolveFactoriesAssignments(
                    refEq(campaign),
                    listOf(factory1, factory2, factory3),
                    listOf(scenario1, scenario2)
                )
                factoryService.releaseFactories(refEq(campaign), listOf("factory-2"))
                headChannel.subscribeFeedback("feedbacks")
                campaignManager.set(capture(newState))
                headChannel.publishDirective(capture(sentDirectives))
                headChannel.publishDirective(capture(sentDirectives))
            }
            assertThat(newState.captured).isInstanceOf(FactoryAssignmentState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("context").isSameAs(campaignExecutionContext)
                prop("operations").isSameAs(operations)
                typedProp<Boolean>("initialized").isTrue()
            }
            assertThat(sentDirectives).all {
                hasSize(2)
                containsOnly(
                    FactoryAssignmentDirective(
                        "my-campaign", mapOf(
                            "scenario-1" to listOf("dag-1", "dag-2"),
                            "scenario-2" to listOf("dag-A", "dag-B")
                        ), "directives-broadcast", "feedbacks", "unicast-channel-1"
                    ),
                    FactoryAssignmentDirective(
                        "my-campaign", mapOf(
                            "scenario-2" to listOf("dag-A", "dag-B", "dag-C")
                        ), "directives-broadcast", "feedbacks", "unicast-channel-3"
                    )
                )
            }
        }

    @Test
    internal fun `should close the campaign when an exception occurs`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                id = "my-campaign",
                scenarios = mapOf("scenario-1" to relaxedMockk())
            )
            coEvery { factoryService.getActiveScenarios(setOf("scenario-1")) } returns
                    listOf(relaxedMockk { every { id } returns "scenario-1" })
            coEvery { factoryService.getAvailableFactoriesForScenarios(any()) } returns
                    listOf(relaxedMockk { every { nodeId } returns "factory-1" })
            coEvery { factoryService.lockFactories(any(), any()) } throws RuntimeException("Something wrong occurred")

            // when
            assertThrows<RuntimeException> {
                campaignManager.start(campaign)
            }

            // then
            coVerifyOrder {
                factoryService.getActiveScenarios(setOf("scenario-1"))
                factoryService.getAvailableFactoriesForScenarios(setOf("scenario-1"))
                campaignService.save(refEq(campaign))
                factoryService.lockFactories(refEq(campaign), listOf("factory-1"))
                campaignService.close("my-campaign", ExecutionStatus.FAILED)
            }
        }

    @Test
    internal fun `should not start a new campaign when some scenarios are currently not supported`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                id = "my-campaign",
                scenarios = mapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
            )
            val scenario1 = relaxedMockk<ScenarioSummary> { every { id } returns "scenario-1" }
            val scenario3 = relaxedMockk<ScenarioSummary> { every { id } returns "scenario-1" }
            coEvery { factoryService.getActiveScenarios(setOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario3)

            // when + then
            assertThrows<IllegalArgumentException> {
                campaignManager.start(campaign)
            }
        }

    @Test
    internal fun `should return RedisFactoryAssignmentState`() =
        testDispatcherProvider.run {
            // given
            val campaign = relaxedMockk<CampaignConfiguration>()
            coEvery { operations.getState("my-campaign") } returns (campaign to CampaignRedisState.FACTORY_DAGS_ASSIGNMENT_STATE)

            // when
            val state = campaignManager.get("my-campaign")

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
        testDispatcherProvider.run {
            // given
            val campaign = relaxedMockk<CampaignConfiguration>()
            coEvery { operations.getState("my-campaign") } returns (campaign to CampaignRedisState.MINIONS_ASSIGNMENT_STATE)

            // when
            val state = campaignManager.get("my-campaign")

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
        testDispatcherProvider.run {
            // given
            val campaign = relaxedMockk<CampaignConfiguration>()
            coEvery { operations.getState("my-campaign") } returns (campaign to CampaignRedisState.WARMUP_STATE)

            // when
            val state = campaignManager.get("my-campaign")

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
        testDispatcherProvider.run {
            // given
            val campaign = relaxedMockk<CampaignConfiguration>()
            coEvery { operations.getState("my-campaign") } returns (campaign to CampaignRedisState.MINIONS_STARTUP_STATE)

            // when
            val state = campaignManager.get("my-campaign")

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
        testDispatcherProvider.run {
            // given
            val campaign = relaxedMockk<CampaignConfiguration>()
            coEvery { operations.getState("my-campaign") } returns (campaign to CampaignRedisState.RUNNING_STATE)

            // when
            val state = campaignManager.get("my-campaign")

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
        testDispatcherProvider.run {
            // given
            val campaign = relaxedMockk<CampaignConfiguration>()
            coEvery { operations.getState("my-campaign") } returns (campaign to CampaignRedisState.COMPLETION_STATE)

            // when
            val state = campaignManager.get("my-campaign")

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
        testDispatcherProvider.run {
            // given
            val campaign = relaxedMockk<CampaignConfiguration>()
            coEvery { operations.getState("my-campaign") } returns (campaign to CampaignRedisState.FAILURE_STATE)

            // when
            val state = campaignManager.get("my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("operations").isSameAs(operations)
                prop("context").isSameAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
        }
}