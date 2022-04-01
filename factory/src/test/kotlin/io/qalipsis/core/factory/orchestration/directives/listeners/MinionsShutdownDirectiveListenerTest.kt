package io.qalipsis.core.factory.orchestration.directives.listeners

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsShutdownFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class MinionsShutdownDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @InjectMockKs
    private lateinit var processor: MinionsShutdownDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept minion shutdown directive`() {
        val directive = MinionsShutdownDirective(
            campaignName = "my-campaign",
            scenarioName = "my-scenario",
            minionIds = listOf("my-minion-1", "my-minion-2"),
            ""
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true
        every { minionsKeeper.contains("my-minion-1") } returns false
        every { minionsKeeper.contains("my-minion-2") } returns true

        assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept minion shutdown directive when the scenario is not executed locally`() {
        val directive = MinionsShutdownDirective(
            campaignName = "my-campaign",
            scenarioName = "my-scenario",
            minionIds = listOf("my-minion-1", "my-minion-2"),
            ""
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false
        every { minionsKeeper.contains("my-minion-1") } returns false
        every { minionsKeeper.contains("my-minion-2") } returns true

        assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept minion shutdown directive when no minion is executed locally`() {
        val directive = MinionsShutdownDirective(
            campaignName = "my-campaign",
            scenarioName = "my-scenario",
            minionIds = listOf("my-minion-1", "my-minion-2"),
            ""
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true
        every { minionsKeeper.contains(any()) } returns false

        assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept not factory assignment directive`() {
        assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    fun `should process the directive and confirm when all is right`() = testCoroutineDispatcher.runTest {
        val directive = MinionsShutdownDirective(
            campaignName = "my-campaign",
            scenarioName = "my-scenario",
            minionIds = listOf("my-minion-1", "my-minion-2", "my-minion-3"),
            ""
        )
        every { minionsKeeper.contains("my-minion-1") } returns true
        every { minionsKeeper.contains("my-minion-2") } returns false
        every { minionsKeeper.contains("my-minion-3") } returns true

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            minionsKeeper.contains("my-minion-1")
            minionsKeeper.contains("my-minion-2")
            minionsKeeper.contains("my-minion-3")
            factoryChannel.publishFeedback(
                MinionsShutdownFeedback(
                    campaignName = "my-campaign",
                    scenarioName = "my-scenario",
                    minionIds = listOf("my-minion-1", "my-minion-3"),
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.shutdownMinions("my-campaign", listOf("my-minion-1", "my-minion-3"))
            factoryChannel.publishFeedback(
                MinionsShutdownFeedback(
                    campaignName = "my-campaign",
                    scenarioName = "my-scenario",
                    minionIds = listOf("my-minion-1", "my-minion-3"),
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(factoryCampaignManager, factoryChannel, minionsKeeper)
    }

    @Test
    fun `should process the directive and not fail even when there is an exception`() =
        testCoroutineDispatcher.runTest {
            val directive = MinionsShutdownDirective(
                campaignName = "my-campaign",
                scenarioName = "my-scenario",
                minionIds = listOf("my-minion-1", "my-minion-2", "my-minion-3"),
                ""
            )
            every { minionsKeeper.contains("my-minion-1") } returns true
            every { minionsKeeper.contains("my-minion-2") } returns false
            every { minionsKeeper.contains("my-minion-3") } returns true
            coEvery {
                factoryCampaignManager.shutdownMinions(
                    "my-campaign",
                    listOf("my-minion-1", "my-minion-3")
                )
            } throws RuntimeException("A problem occurred")

            // when
            processor.notify(directive)

            // then
            coVerifyOrder {
                minionsKeeper.contains("my-minion-1")
                minionsKeeper.contains("my-minion-2")
                minionsKeeper.contains("my-minion-3")
                factoryChannel.publishFeedback(
                    MinionsShutdownFeedback(
                        campaignName = "my-campaign",
                        scenarioName = "my-scenario",
                        minionIds = listOf("my-minion-1", "my-minion-3"),
                        status = FeedbackStatus.IN_PROGRESS
                    )
                )
                factoryCampaignManager.shutdownMinions("my-campaign", listOf("my-minion-1", "my-minion-3"))
                factoryChannel.publishFeedback(
                    MinionsShutdownFeedback(
                        campaignName = "my-campaign",
                        scenarioName = "my-scenario",
                        minionIds = listOf("my-minion-1", "my-minion-3"),
                        status = FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(factoryCampaignManager, factoryChannel, minionsKeeper)
        }
}