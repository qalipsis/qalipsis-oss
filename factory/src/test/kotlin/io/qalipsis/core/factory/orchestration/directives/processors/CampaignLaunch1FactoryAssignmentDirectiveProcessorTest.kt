package io.qalipsis.core.factory.orchestration.directives.processors

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignLaunch1FactoryAssignmentDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var processor: CampaignLaunch1FactoryAssignmentDirectiveProcessor

    @BeforeEach
    internal fun setUp() {
        every { idGenerator.short() } returnsMany (1..3).map { "the-feedback-$it" }
        every { factoryCampaignManager.feedbackNodeId } returns "my-factory"
    }

    @Test
    @Timeout(1)
    fun `should accept factory assignment directive`() {
        val directive = FactoryAssignmentDirective(
            "my-campaign",
            mapOf("my-scenario-1" to listOf("dag-1", "dag-2"), "my-scenario-2" to listOf("dag-3", "dag-4")),
            "my-directive",
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
            "my-directive",
            channel = "broadcast"
        )

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                FactoryAssignmentFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.initCampaign("my-campaign", setOf("my-scenario-1", "my-scenario-2"))
            minionAssignmentKeeper.assignFactoryDags(
                "my-campaign",
                mapOf("my-scenario-1" to listOf("dag-1", "dag-2"), "my-scenario-2" to listOf("dag-3", "dag-4"))
            )
            feedbackFactoryChannel.publish(
                FactoryAssignmentFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    nodeId = "my-factory",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(factoryCampaignManager, minionAssignmentKeeper, feedbackFactoryChannel)
    }

    @Test
    fun `should process the directive and fails when there is an exception`() =
        testCoroutineDispatcher.runTest {
            val directive = FactoryAssignmentDirective(
                "my-campaign",
                mapOf("my-scenario-1" to listOf("dag-1", "dag-2"), "my-scenario-2" to listOf("dag-3", "dag-4")),
                "my-directive",
                channel = "broadcast"
            )
            coEvery { factoryCampaignManager.initCampaign(any(), any()) } throws RuntimeException("A problem occurred")

            // when
            processor.process(directive)

            // then
            coVerifyOrder {
                factoryCampaignManager.feedbackNodeId
                feedbackFactoryChannel.publish(
                    FactoryAssignmentFeedback(
                        key = "the-feedback-1",
                        campaignId = "my-campaign",
                        nodeId = "my-factory",
                        status = FeedbackStatus.IN_PROGRESS
                    )
                )
                factoryCampaignManager.initCampaign("my-campaign", setOf("my-scenario-1", "my-scenario-2"))
                feedbackFactoryChannel.publish(
                    FactoryAssignmentFeedback(
                        key = "the-feedback-2",
                        campaignId = "my-campaign",
                        nodeId = "my-factory",
                        status = FeedbackStatus.FAILED,
                        error = "A problem occurred"
                    )
                )
            }
            confirmVerified(factoryCampaignManager, minionAssignmentKeeper, feedbackFactoryChannel)
        }
}