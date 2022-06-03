package io.qalipsis.core.factory.orchestration.directives.listeners

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.AbortCampaignConfiguration
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignAbortDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @InjectMockKs
    private lateinit var listener: CampaignAbortDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept campaign abort directive`() {
        val directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "broadcast",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortCampaignConfiguration = AbortCampaignConfiguration()
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign") } returns true

        Assertions.assertTrue(listener.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept campaign abort directive when the campaign is not executed locally`() {
        val directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "broadcast",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortCampaignConfiguration = AbortCampaignConfiguration()
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign") } returns false

        Assertions.assertFalse(listener.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept not campaign abort directive`() {
        Assertions.assertFalse(listener.accept(TestDescriptiveDirective()))
    }

    @Test
    fun `should process the directive and confirm when all is right`() = testCoroutineDispatcher.runTest {
        val directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "broadcast",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortCampaignConfiguration = AbortCampaignConfiguration()
        )

        // when
        listener.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                CampaignAbortFeedback(
                    campaignKey = "my-campaign",
                    status = FeedbackStatus.IN_PROGRESS,
                    scenarioNames = listOf("my-scenario-1", "my-scenario-2")
                )
            )
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario-1")
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario-2")
            factoryChannel.publishFeedback(
                CampaignAbortFeedback(
                    campaignKey = "my-campaign",
                    status = FeedbackStatus.COMPLETED,
                    scenarioNames = listOf("my-scenario-1", "my-scenario-2")
                )
            )
        }
        confirmVerified(factoryCampaignManager, factoryChannel)
    }

    @Test
    fun `should process the directive and fail when there is an exception`() = testCoroutineDispatcher.runTest {
        val directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "broadcast",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortCampaignConfiguration = AbortCampaignConfiguration()
        )
        coEvery { factoryCampaignManager.shutdownScenario(any(), any()) } throws RuntimeException("A problem occurred")

        // when
        listener.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                CampaignAbortFeedback(
                    campaignKey = "my-campaign",
                    status = FeedbackStatus.IN_PROGRESS,
                    scenarioNames = listOf("my-scenario-1", "my-scenario-2")
                )
            )
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario-1")
            factoryChannel.publishFeedback(
                CampaignAbortFeedback(
                    campaignKey = "my-campaign",
                    status = FeedbackStatus.FAILED,
                    scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
                    error = "A problem occurred"
                )
            )
        }
        confirmVerified(factoryCampaignManager, factoryChannel)
    }
}