package io.qalipsis.core.head.campaign.states

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class FailureStateTest : AbstractStateTest() {

    @Test
    internal fun `should be a completion state`() {
        assertThat(FailureState(campaign, "").isCompleted).isFalse()
    }

    @Test
    internal fun `should return shutdown directive on init`() = testDispatcherProvider.runTest {
        // when
        val directives = FailureState(campaign, "this error").run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        verify { campaign setProperty "message" value "this error" }
        assertThat(directives).all {
            hasSize(1)
            containsOnly(
                CampaignShutdownDirective("my-campaign", "my-broadcast-channel")
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
            val state = FailureState(campaign, "this error")
            state.run {
                inject(campaignExecutionContext)
                init()
            }

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
                typedProp<Boolean>("isSuccessful").isFalse()
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce {
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED)
            }
            confirmVerified(campaignService, factoryService, campaignReportStateKeeper)
        }
}