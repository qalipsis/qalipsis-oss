package io.qalipsis.core.head.campaign.states

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.rampup.RampUpConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class MinionsStartupStateTest : AbstractStateTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(MinionsStartupState(campaign).isCompleted).isFalse()
    }

    @Test
    fun `should return the directives for ramp-up for each scenario`() = testDispatcherProvider.runTest {
        // given
        val state = MinionsStartupState(campaign)
        every { campaign.startOffsetMs } returns 1234L
        every { campaign.speedFactor } returns 153.42
        every { campaign.scenarios } returns mapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())

        // when
        val directives = state.init(factoryService, campaignReportStateKeeper, idGenerator)

        // then
        assertThat(directives).all {
            hasSize(2)
            containsOnly(
                MinionsRampUpPreparationDirective(
                    "my-campaign",
                    "scenario-1",
                    RampUpConfiguration(1234L, 153.42),
                    "the-directive-1",
                    "my-broadcast-channel"
                ),
                MinionsRampUpPreparationDirective(
                    "my-campaign",
                    "scenario-2",
                    RampUpConfiguration(1234L, 153.42),
                    "the-directive-2",
                    "my-broadcast-channel"
                )
            )
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.runTest {
        // given
        val state = MinionsStartupState(campaign)
        state.init(factoryService, campaignReportStateKeeper, idGenerator)

        // when
        var newState = state.process(mockk<MinionsDeclarationFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 1"
        })

        // then
        assertThat(newState).isInstanceOf(FailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 1")
        }

        // when
        newState = state.process(mockk<MinionsRampUpPreparationFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 2"
        })

        // then
        assertThat(newState).isInstanceOf(FailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 2")
        }

        // when
        newState = state.process(mockk<MinionsStartFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 3"
        })

        // then
        assertThat(newState).isInstanceOf(FailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 3")
        }

        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.runTest {
            // given
            val state = MinionsStartupState(campaign)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)
            val feedback = mockk<MinionsStartFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.FAILED
                every { error } returns null
            }

            // when
            val newState = state.process(feedback)

            // then
            assertThat(newState).isInstanceOf(FailureState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("error").isEqualTo("")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return itself in case of any unsupported feedback`() =
        testDispatcherProvider.runTest {
            // given
            val state = MinionsStartupState(campaign)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // when
            val newState = state.process(mockk<Feedback>())

            // then
            assertThat(newState).isSameAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RunningState when a MinionsStartDirective is received`() =
        testDispatcherProvider.runTest {
            // given
            val state = MinionsStartupState(campaign)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // when
            val newState = state.process(mockk<MinionsStartDirective>())

            // then
            assertThat(newState).isInstanceOf(RunningState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
                typedProp<Collection<Directive>>("directivesForInit").isEmpty()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

}