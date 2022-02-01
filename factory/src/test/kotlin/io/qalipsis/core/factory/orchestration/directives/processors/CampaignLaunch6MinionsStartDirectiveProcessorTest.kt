package io.qalipsis.core.factory.orchestration.directives.processors

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignLaunch6MinionsStartDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    protected lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var processor: CampaignLaunch6MinionsStartDirectiveProcessor

    @BeforeEach
    internal fun setUp() {
        every { idGenerator.short() } returnsMany (1..3).map { "the-feedback-$it" }
        every { factoryCampaignManager.feedbackNodeId } returns "my-factory"
    }

    @Test
    @Timeout(1)
    internal fun `should accept minions start directive`() {
        val directive = MinionsStartDirective(
            "my-campaign",
            "my-scenario",
            listOf(),
            "my-directive",
            channel = "broadcast",
        )
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))
        verify { localAssignmentStore.hasMinionsAssigned("my-scenario") }
    }

    @Test
    @Timeout(1)
    internal fun `should not accept minions start directive when there is no assignment`() {
        val directive = MinionsStartDirective(
            "my-campaign",
            "my-scenario",
            listOf(),
            "my-directive",
            channel = "broadcast",
        )
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
        verify { localAssignmentStore.hasMinionsAssigned("my-scenario") }
    }

    @Test
    @Timeout(1)
    internal fun `should not accept not minions start directive`() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    @Timeout(1)
    internal fun `should start the minions executing the root DAG under load locally`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsStartDirective(
            "my-campaign",
            "my-scenario",
            listOf(
                MinionStartDefinition("my-minion-1", 123),
                MinionStartDefinition("my-minion-2", 456),
                MinionStartDefinition("my-minion-3", 789)
            ),
            "my-directive",
            channel = "broadcast",
        )
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1") } returns true
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2") } returns false
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3") } returns true

        // when
        processor.process(directive)

        // then
        coVerify {
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3")
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsStartFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionsKeeper.scheduleMinionStart("my-minion-1", Instant.ofEpochMilli(123))
            minionsKeeper.scheduleMinionStart("my-minion-3", Instant.ofEpochMilli(789))
            feedbackFactoryChannel.publish(
                MinionsStartFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(minionsKeeper, localAssignmentStore, feedbackFactoryChannel)
    }

    @Test
    @Timeout(1)
    internal fun `should do nothing when no minion executes the root DAG under load locally`() =
        testCoroutineDispatcher.run {
            // given
            val directive = MinionsStartDirective(
                "my-campaign",
                "my-scenario",
                listOf(
                    MinionStartDefinition("my-minion-1", 123),
                    MinionStartDefinition("my-minion-2", 456),
                    MinionStartDefinition("my-minion-3", 789)
                ),
                "my-directive",
                channel = "broadcast",
            )
            every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", any()) } returns false

            // when
            processor.process(directive)

            // then
            coVerify {
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1")
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2")
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3")
                factoryCampaignManager.feedbackNodeId
                feedbackFactoryChannel.publish(
                    MinionsStartFeedback(
                        key = "the-feedback-1",
                        campaignId = "my-campaign",
                        scenarioId = "my-scenario",
                        nodeId = "my-factory",
                        status = FeedbackStatus.IGNORED
                    )
                )
            }
            confirmVerified(minionsKeeper, localAssignmentStore, feedbackFactoryChannel)
        }


    @Test
    @Timeout(1)
    internal fun `should fail to start when a problem occurs`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsStartDirective(
            "my-campaign",
            "my-scenario",
            listOf(
                MinionStartDefinition("my-minion-1", 123),
                MinionStartDefinition("my-minion-2", 456),
                MinionStartDefinition("my-minion-3", 789)
            ),
            "my-directive",
            channel = "broadcast",
        )
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", any()) } returns true
        coEvery {
            minionsKeeper.scheduleMinionStart(
                "my-minion-1",
                Instant.ofEpochMilli(123)
            )
        } throws RuntimeException("A problem occurred")

        // when
        processor.process(directive)

        // then
        coVerify {
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3")
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsStartFeedback(
                    key = "the-feedback-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionsKeeper.scheduleMinionStart("my-minion-1", Instant.ofEpochMilli(123))
            feedbackFactoryChannel.publish(
                MinionsStartFeedback(
                    key = "the-feedback-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }
        confirmVerified(minionsKeeper, localAssignmentStore, feedbackFactoryChannel)
    }
}
