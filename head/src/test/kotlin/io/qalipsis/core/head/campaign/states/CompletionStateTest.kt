package io.qalipsis.core.head.campaign.states

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.head.model.NodeId
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class CompletionStateTest : AbstractStateTest() {

    @Test
    fun `should be a completion state`() {
        assertThat(CompletionState(campaign).isCompleted).isFalse()
    }

    @Test
    fun `should return shutdown directive on init`() = testDispatcherProvider.runTest {
        // when
        val directives = CompletionState(campaign).init(factoryService, campaignReportStateKeeper, idGenerator)

        // then
        assertThat(directives).all {
            hasSize(1)
            containsOnly(
                CampaignShutdownDirective("my-campaign", "the-directive-1", "my-broadcast-channel")
            )
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return DisabledState when all the feedbacks were received`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk(),
                "node-2" to relaxedMockk()
            )
            val state = CompletionState(campaign)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // when
            var newState = state.process(mockk<CampaignShutdownFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).all {
                isSameAs(state)
                typedProp<Collection<NodeId>>("expectedFeedbacks").containsOnly("node-2")
            }

            // when
            newState = state.process(mockk<CampaignShutdownFeedback> {
                every { nodeId } returns "node-2"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(DisabledState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("isSuccessful").isTrue()
                typedProp<Boolean>("initialized").isFalse()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

}