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
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.core.configuration.AbortCampaignConfiguration
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class AbortingStateTest : AbstractStateTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(AbortingState(campaign, AbortCampaignConfiguration(), "").isCompleted).isFalse()
    }

    @Test
    fun `should return campaign abort directive on init`() = testDispatcherProvider.runTest {
        // given
        every { campaign.scenarios } returns mapOf(
            "scenario-1" to ScenarioConfiguration(2),
            "scenario-2" to ScenarioConfiguration(5)
        )

        // when
        val directives = AbortingState(campaign, AbortCampaignConfiguration(), "").run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(directives).all {
            hasSize(1)
            containsOnly(
                CampaignAbortDirective(
                    "my-campaign",
                    "my-broadcast-channel",
                    listOf("scenario-1", "scenario-2")
                )
            )
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a new CompletionState when a CampaignAbortFeedback and all the feedbacks were received and the abort is soft`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf("node-1" to relaxedMockk(),)

            val state = AbortingState(campaign, AbortCampaignConfiguration(hard = false), "")
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<CampaignAbortFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(CompletionState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new FailureState when a CampaignAbortFeedback and all the feedbacks were received and the abort is hard`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf("node-1" to relaxedMockk())

            val state = AbortingState(campaign, AbortCampaignConfiguration(hard = true),"")
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<CampaignAbortFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(FailureState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("error").isSameAs("The campaign was aborted")
                typedProp<Boolean>("initialized").isFalse()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new AbortingState when a CampaignAbortFeedback was received`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk(),
                "node-2" to relaxedMockk()
            )

            val state = AbortingState(campaign, AbortCampaignConfiguration(),"this error")
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<CampaignAbortFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(AbortingState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("error").isSameAs("this error")
                typedProp<Boolean>("initialized").isTrue()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }
}