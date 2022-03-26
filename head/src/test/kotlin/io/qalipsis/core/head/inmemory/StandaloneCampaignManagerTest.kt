package io.qalipsis.core.head.inmemory

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
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.FactoryConfiguration
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.EmptyState
import io.qalipsis.core.head.campaign.states.FactoryAssignmentState
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.inmemory.catadioptre.currentCampaignState
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class StandaloneCampaignManagerTest {

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
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @RelaxedMockK
    private lateinit var campaignExecutionContext: CampaignExecutionContext

    @InjectMockKs
    @SpyK(recordPrivateCalls = true)
    private lateinit var campaignManager: StandaloneCampaignManager

    @AfterEach
    internal fun tearDown() {
        campaignManager.currentCampaignState(EmptyState)
    }

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
            ).apply { broadcastChannel = "my-broadcast-channel" }
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
            assertThat(campaignManager).typedProp<CampaignExecutionState<CampaignExecutionContext>>("currentCampaignState")
                .isInstanceOf(FactoryAssignmentState::class).all {
                    prop("campaign").isSameAs(campaign)
                    typedProp<Boolean>("initialized").isTrue()
                }
            val sentDirectives = mutableListOf<Directive>()
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
                headChannel.publishDirective(capture(sentDirectives))
                headChannel.publishDirective(capture(sentDirectives))
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
    internal fun `should not start a new campaign when some scenarios are currently not supported`() =
        testDispatcherProvider.run {
            // given
            val campaign = CampaignConfiguration(
                id = "my-campaign",
                scenarios = mapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
            ).apply { broadcastChannel = "my-broadcast-channel" }
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
    internal fun `should not start a new campaign when one is already running`() =
        testDispatcherProvider.run {
            // given
            campaignManager.setProperty("currentCampaignState",
                relaxedMockk<CampaignExecutionState<CampaignExecutionContext>> {
                    every { isCompleted } returns false
                })

            // when + then
            assertThrows<IllegalArgumentException> {
                campaignManager.start(relaxedMockk())
            }
        }
}