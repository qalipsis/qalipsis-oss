package io.qalipsis.core.factory.orchestration.directives.processors

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.mockk.verifyOrder
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignLaunch4ScenarioWarmUpDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var processor: CampaignLaunch4ScenarioWarmUpDirectiveProcessor

    @BeforeEach
    internal fun setUp() {
        every { idGenerator.short() } returnsMany (1..3).map { "the-feedback-$it" }
        every { factoryCampaignManager.feedbackNodeId } returns "my-factory"
    }

    @Test
    @Timeout(1)
    fun `should accept campaign warm-up directive`() {
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", channel = "broadcast", key = "my-directive")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        assertTrue(processor.accept(directive))
        verifyOrder {
            factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario")
        }
        confirmVerified(localAssignmentStore, factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept not campaign warm-up directive`() {
        assertFalse(processor.accept(TestDescriptiveDirective()))

        confirmVerified(localAssignmentStore, factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept campaign warm-up directive for unknown scenario`() {
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", channel = "broadcast", key = "my-directive")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        assertFalse(processor.accept(directive))

        verify { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
        confirmVerified(localAssignmentStore, factoryCampaignManager)
    }

    @Test
    internal fun `should warm up the campaign successfully`() = testCoroutineDispatcher.runTest {
        // given
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", channel = "broadcast", key = "my-directive")
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns true

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            localAssignmentStore.hasMinionsAssigned("my-scenario")
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                ScenarioWarmUpFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")
            feedbackFactoryChannel.publish(
                ScenarioWarmUpFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(localAssignmentStore, feedbackFactoryChannel, factoryCampaignManager)
    }


    @Test
    internal fun `should ignore when no minion is locally assigned`() = testCoroutineDispatcher.runTest {
        // given
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", channel = "broadcast", key = "my-directive")
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns false

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            localAssignmentStore.hasMinionsAssigned("my-scenario")
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                ScenarioWarmUpFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IGNORED
                )
            )
        }
        confirmVerified(localAssignmentStore, feedbackFactoryChannel, factoryCampaignManager)
    }


    @Test
    internal fun `should fail to warm up the campaign`() = testCoroutineDispatcher.runTest {
        // given
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", channel = "broadcast", key = "my-directive")
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns true
        coEvery {
            factoryCampaignManager.warmUpCampaignScenario(any(), any())
        } throws RuntimeException("A problem occurred")

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            localAssignmentStore.hasMinionsAssigned("my-scenario")
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                ScenarioWarmUpFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")
            feedbackFactoryChannel.publish(
                ScenarioWarmUpFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }
        confirmVerified(localAssignmentStore, feedbackFactoryChannel, factoryCampaignManager)
    }
}
