package io.qalipsis.core.factory.orchestration.directives.processors

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignLaunch3MinionsAssignmentDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var processor: CampaignLaunch3MinionsAssignmentDirectiveProcessor

    @BeforeEach
    internal fun setUp() {
        every { idGenerator.short() } returnsMany (1..3).map { "the-feedback-$it" }
        every { factoryCampaignManager.feedbackNodeId } returns "my-factory"
    }

    @Test
    @Timeout(1)
    fun `should accept minions assignment directive`() {
        val directive =
            MinionsAssignmentDirective(
                "my-campaign", "my-scenario", "my-directive", channel = "broadcast",
            )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(1)
    fun `should not accept not minions assignment directive`() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
        confirmVerified(factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept minions assignment directive for unknown scenario`() {
        val directive =
            MinionsAssignmentDirective(
                "my-campaign", "my-scenario", "my-directive", channel = "broadcast",
            )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(1)
    fun `should assign and create the minions successfully`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsAssignmentDirective("my-campaign", "my-scenario", "my-directive", channel = "broadcast")
        coEvery { minionAssignmentKeeper.assign("my-campaign", "my-scenario") } returns mapOf(
            "minion-1" to listOf("dag-1", "dag-2"),
            "minion-2" to listOf("dag-2", "dag-3")
        )

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsAssignmentFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionAssignmentKeeper.assign("my-campaign", "my-scenario")
            minionsKeeper.create("my-campaign", "my-scenario", listOf("dag-1", "dag-2"), "minion-1")
            minionsKeeper.create("my-campaign", "my-scenario", listOf("dag-2", "dag-3"), "minion-2")
            feedbackFactoryChannel.publish(
                MinionsAssignmentFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(minionAssignmentKeeper, minionsKeeper, feedbackFactoryChannel)
    }


    @Test
    @Timeout(1)
    fun `should assign and ignore when no minion was assigned`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsAssignmentDirective("my-campaign", "my-scenario", "my-directive", channel = "broadcast")
        coEvery { minionAssignmentKeeper.assign("my-campaign", "my-scenario") } returns emptyMap()

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsAssignmentFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionAssignmentKeeper.assign("my-campaign", "my-scenario")
            feedbackFactoryChannel.publish(
                MinionsAssignmentFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IGNORED
                )
            )
        }
        confirmVerified(minionAssignmentKeeper, minionsKeeper, feedbackFactoryChannel)
    }


    @Test
    @Timeout(1)
    fun `should fail to assign and create the minions`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsAssignmentDirective("my-campaign", "my-scenario", "my-directive", channel = "broadcast")
        coEvery {
            minionAssignmentKeeper.assign(
                "my-campaign",
                "my-scenario"
            )
        } throws RuntimeException("A problem occurred")

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsAssignmentFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionAssignmentKeeper.assign("my-campaign", "my-scenario")
            feedbackFactoryChannel.publish(
                MinionsAssignmentFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }
        confirmVerified(minionAssignmentKeeper, minionsKeeper, feedbackFactoryChannel)
    }

}
