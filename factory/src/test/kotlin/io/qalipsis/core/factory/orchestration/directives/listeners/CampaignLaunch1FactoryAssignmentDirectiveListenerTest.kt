package io.qalipsis.core.factory.orchestration.directives.listeners

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignLaunch1FactoryAssignmentDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var campaignLifeCycleAware1: CampaignLifeCycleAware

    @RelaxedMockK
    private lateinit var campaignLifeCycleAware2: CampaignLifeCycleAware

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    private val campaignLifeCycleAwares: Collection<CampaignLifeCycleAware> by lazy {
        listOf(campaignLifeCycleAware1, campaignLifeCycleAware2)
    }

    @InjectMockKs
    private lateinit var processor: CampaignLaunch1FactoryAssignmentDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept factory assignment directive`() {
        val directive = FactoryAssignmentDirective(
            "my-campaign",
            mapOf("my-scenario-1" to listOf("dag-1", "dag-2"), "my-scenario-2" to listOf("dag-3", "dag-4")),
            broadcastChannel = "broadcast-channel",
            feedbackChannel = "feedback-channel",
            channel = "broadcast"
        )

        assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept not factory assignment directive`() {
        assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    fun `should process the directive and confirm when all is right`() = testCoroutineDispatcher.runTest {
        val directive = FactoryAssignmentDirective(
            "my-campaign",
            mapOf("my-scenario-1" to listOf("dag-1", "dag-2"), "my-scenario-2" to listOf("dag-3", "dag-4")),
            broadcastChannel = "broadcast-channel",
            feedbackChannel = "feedback-channel",
            channel = "broadcast"
        )

        // when
        processor.notify(directive)

        // then
        val expectedCampaign = Campaign(
            campaignId = "my-campaign",
            broadcastChannel = "broadcast-channel",
            feedbackChannel = "feedback-channel",
            assignedDagsByScenario = mapOf(
                "my-scenario-1" to listOf("dag-1", "dag-2"),
                "my-scenario-2" to listOf("dag-3", "dag-4")
            )
        )
        coVerifyOrder {
            campaignLifeCycleAware1.init(expectedCampaign)
            campaignLifeCycleAware2.init(expectedCampaign)
            factoryChannel.publishFeedback(
                FactoryAssignmentFeedback(campaignId = "my-campaign", status = FeedbackStatus.IN_PROGRESS)
            )
            minionAssignmentKeeper.assignFactoryDags(
                "my-campaign",
                mapOf("my-scenario-1" to listOf("dag-1", "dag-2"), "my-scenario-2" to listOf("dag-3", "dag-4"))
            )
            factoryChannel.publishFeedback(
                FactoryAssignmentFeedback(campaignId = "my-campaign", status = FeedbackStatus.COMPLETED)
            )
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, campaignLifeCycleAware1, campaignLifeCycleAware2)
    }

    @Test
    fun `should process the directive and fails when there is an exception`() =
        testCoroutineDispatcher.runTest {
            val directive = FactoryAssignmentDirective(
                "my-campaign",
                mapOf("my-scenario-1" to listOf("dag-1", "dag-2"), "my-scenario-2" to listOf("dag-3", "dag-4")),
                broadcastChannel = "broadcast-channel",
                feedbackChannel = "feedback-channel",
                channel = "broadcast"
            )
            coEvery { campaignLifeCycleAware1.init(any()) } throws RuntimeException("A problem occurred")

            // when
            processor.notify(directive)

            // then
            val expectedCampaign = Campaign(
                campaignId = "my-campaign",
                broadcastChannel = "broadcast-channel",
                feedbackChannel = "feedback-channel",
                assignedDagsByScenario = mapOf(
                    "my-scenario-1" to listOf("dag-1", "dag-2"),
                    "my-scenario-2" to listOf("dag-3", "dag-4")
                )
            )
            coVerifyOrder {
                campaignLifeCycleAware1.init(expectedCampaign)
                factoryChannel.publishFeedback(
                    FactoryAssignmentFeedback(
                        campaignId = "my-campaign",
                        status = FeedbackStatus.FAILED,
                        error = "A problem occurred"
                    )
                )
            }
            confirmVerified(factoryChannel, minionAssignmentKeeper, campaignLifeCycleAware1, campaignLifeCycleAware2)
        }
}