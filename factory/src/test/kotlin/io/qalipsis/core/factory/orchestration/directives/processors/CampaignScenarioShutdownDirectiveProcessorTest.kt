package io.qalipsis.core.factory.orchestration.directives.processors

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignScenarioShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignScenarioShutdownDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var processor: CampaignScenarioShutdownDirectiveProcessor

    @BeforeEach
    internal fun setUp() {
        every { idGenerator.short() } returnsMany (1..3).map { "the-feedback-$it" }
        every { factoryCampaignManager.feedbackNodeId } returns "my-factory"
    }

    @Test
    @Timeout(1)
    fun `should accept campaign scenario shutdown directive`() {
        val directive = CampaignScenarioShutdownDirective(
            "my-campaign",
            "my-scenario",
            "my-directive",
            channel = "broadcast"
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept campaign scenario shutdown directive when the scenario is not executed locally`() {
        val directive = CampaignScenarioShutdownDirective(
            "my-campaign",
            "my-scenario",
            "my-directive",
            channel = "broadcast"
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept not campaign scenario shutdown directive`() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    fun `should process the directive and confirm when all is right`() = testCoroutineDispatcher.runTest {
        val directive = CampaignScenarioShutdownDirective(
            "my-campaign",
            "my-scenario",
            "my-directive",
            channel = "broadcast"
        )

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                CampaignScenarioShutdownFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
            feedbackFactoryChannel.publish(
                CampaignScenarioShutdownFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(factoryCampaignManager, feedbackFactoryChannel)
    }

    @Test
    fun `should process the directive and fails when there is an exception`() =
        testCoroutineDispatcher.runTest {
            val directive = CampaignScenarioShutdownDirective(
                "my-campaign",
                "my-scenario",
                "my-directive",
                channel = "broadcast"
            )
            coEvery {
                factoryCampaignManager.shutdownScenario(
                    any(),
                    any()
                )
            } throws RuntimeException("A problem occurred")

            // when
            processor.process(directive)

            // then
            coVerifyOrder {
                factoryCampaignManager.feedbackNodeId
                feedbackFactoryChannel.publish(
                    CampaignScenarioShutdownFeedback(
                        key = "the-feedback-1",
                        campaignId = "my-campaign",
                        scenarioId = "my-scenario",
                        nodeId = "my-factory",
                        status = FeedbackStatus.IN_PROGRESS
                    )
                )
                factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
                feedbackFactoryChannel.publish(
                    CampaignScenarioShutdownFeedback(
                        key = "the-feedback-2",
                        campaignId = "my-campaign",
                        scenarioId = "my-scenario",
                        nodeId = "my-factory",
                        status = FeedbackStatus.FAILED,
                        error = "A problem occurred"
                    )
                )
            }
            confirmVerified(factoryCampaignManager, feedbackFactoryChannel)
        }
}