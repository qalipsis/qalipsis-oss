package io.qalipsis.core.factory.orchestration.directives.processors

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsShutdownFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class MinionsShutdownDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var processor: MinionsShutdownDirectiveProcessor

    @BeforeEach
    internal fun setUp() {
        every { idGenerator.short() } returnsMany (1..3).map { "the-feedback-$it" }
        every { factoryCampaignManager.feedbackNodeId } returns "my-factory"
    }

    @Test
    @Timeout(1)
    fun `should accept minion shutdown directive`() {
        val directive = MinionsShutdownDirective(
            "my-campaign",
            "my-scenario",
            minionIds = listOf("my-minion-1", "my-minion-2"),
            "my-directive",
            channel = "broadcast"
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
            "my-campaign",
            "my-scenario",
            minionIds = listOf("my-minion-1", "my-minion-2"),
            "my-directive",
            channel = "broadcast"
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
            "my-campaign",
            "my-scenario",
            minionIds = listOf("my-minion-1", "my-minion-2"),
            "my-directive",
            channel = "broadcast"
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
            "my-campaign",
            "my-scenario",
            minionIds = listOf("my-minion-1", "my-minion-2", "my-minion-3"),
            "my-directive",
            channel = "broadcast"
        )
        every { minionsKeeper.contains("my-minion-1") } returns true
        every { minionsKeeper.contains("my-minion-2") } returns false
        every { minionsKeeper.contains("my-minion-3") } returns true

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            minionsKeeper.contains("my-minion-1")
            minionsKeeper.contains("my-minion-2")
            minionsKeeper.contains("my-minion-3")
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsShutdownFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    minionIds = listOf("my-minion-1", "my-minion-3"),
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionsKeeper.shutdownMinion("my-minion-1")
            minionsKeeper.shutdownMinion("my-minion-3")
            feedbackFactoryChannel.publish(
                MinionsShutdownFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    minionIds = listOf("my-minion-1", "my-minion-3"),
                    nodeId = "my-factory",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(factoryCampaignManager, feedbackFactoryChannel)
    }

    @Test
    fun `should process the directive and not fail even when there is an exception`() =
        testCoroutineDispatcher.runTest {
            val directive = MinionsShutdownDirective(
                "my-campaign",
                "my-scenario",
                minionIds = listOf("my-minion-1", "my-minion-2", "my-minion-3"),
                "my-directive",
                channel = "broadcast"
            )
            every { minionsKeeper.contains("my-minion-1") } returns true
            every { minionsKeeper.contains("my-minion-2") } returns false
            every { minionsKeeper.contains("my-minion-3") } returns true
            coEvery { minionsKeeper.shutdownMinion("my-minion-1") } throws RuntimeException("A problem occurred")

            // when
            processor.process(directive)

            // then
            coVerifyOrder {
                minionsKeeper.contains("my-minion-1")
                minionsKeeper.contains("my-minion-2")
                minionsKeeper.contains("my-minion-3")
                factoryCampaignManager.feedbackNodeId
                feedbackFactoryChannel.publish(
                    MinionsShutdownFeedback(
                        key = "the-feedback-1",
                        campaignId = "my-campaign",
                        scenarioId = "my-scenario",
                        minionIds = listOf("my-minion-1", "my-minion-3"),
                        nodeId = "my-factory",
                        status = FeedbackStatus.IN_PROGRESS
                    )
                )
                minionsKeeper.shutdownMinion("my-minion-1")
                minionsKeeper.shutdownMinion("my-minion-3")
                feedbackFactoryChannel.publish(
                    MinionsShutdownFeedback(
                        key = "the-feedback-2",
                        campaignId = "my-campaign",
                        scenarioId = "my-scenario",
                        minionIds = listOf("my-minion-1", "my-minion-3"),
                        nodeId = "my-factory",
                        status = FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(factoryCampaignManager, feedbackFactoryChannel)
        }
}