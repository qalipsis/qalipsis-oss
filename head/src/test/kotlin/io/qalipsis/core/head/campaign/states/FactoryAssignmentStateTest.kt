package io.qalipsis.core.head.campaign.states

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.NodeId
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyNever
import org.junit.jupiter.api.Test

internal class FactoryAssignmentStateTest : AbstractStateTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(
            FactoryAssignmentState(campaign).isCompleted
        ).isFalse()
    }

    @Test
    fun `should return assignment directives on init`() = testDispatcherProvider.runTest {
        // given
        every { campaign.broadcastChannel } returns "broadcast-channel"
        every { campaign.feedbackChannel } returns "feedback-channel"
        every { campaign.factories } returns mutableMapOf(
            "node-1" to relaxedMockk {
                every { unicastChannel } returns "unicast-channel-1"
                every { assignment } returns mutableMapOf(
                    "scenario-1" to listOf("dag-1", "dag-2"),
                    "scenario-2" to listOf("dag-A", "dag-B")
                )
            },
            "node-2" to relaxedMockk {
                every { unicastChannel } returns "unicast-channel-2"
                every { assignment } returns mutableMapOf(
                    "scenario-2" to listOf("dag-A", "dag-B", "dag-C")
                )
            }
        )
        val state = FactoryAssignmentState(campaign)
        assertThat(state).typedProp<Collection<NodeId>>("expectedFeedbacks").containsOnly("node-1", "node-2")

        // when
        val directives = state.run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(directives).all {
            hasSize(2)
            containsOnly(
                FactoryAssignmentDirective(
                    "my-campaign", mapOf(
                        "scenario-1" to listOf("dag-1", "dag-2"),
                        "scenario-2" to listOf("dag-A", "dag-B")
                    ), "broadcast-channel", "feedback-channel",
                    "unicast-channel-1"
                ),
                FactoryAssignmentDirective(
                    "my-campaign", mapOf(
                        "scenario-2" to listOf("dag-A", "dag-B", "dag-C")
                    ), "broadcast-channel", "feedback-channel",
                    "unicast-channel-2"
                )
            )
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.runTest {
        // given
        val state = FactoryAssignmentState(campaign)
        state.run {
            inject(campaignExecutionContext)
            init()
        }
        val feedback = mockk<FactoryAssignmentFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error"
        }

        // when
        val newState = state.process(feedback)

        // then
        assertThat(newState).isInstanceOf(FailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.runTest {
            // given
            val state = FactoryAssignmentState(campaign)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val feedback = mockk<FactoryAssignmentFeedback> {
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
            val state = FactoryAssignmentState(campaign)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<Feedback>())

            // then
            assertThat(newState).isSameAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should unassign when feedback is ignored then MinionsAssignmentState when all the feedbacks were received`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk(),
                "node-2" to relaxedMockk()
            )
            val state = FactoryAssignmentState(campaign)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            var newState = state.process(mockk<FactoryAssignmentFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).all {
                isSameAs(state)
                typedProp<Collection<NodeId>>("expectedFeedbacks").containsOnly("node-2")
            }
            coVerifyOnce {
                campaign.unassignFactory("node-1")
                factoryService.releaseFactories(refEq(campaign), listOf("node-1"))
            }

            // when
            newState = state.process(mockk<FactoryAssignmentFeedback> {
                every { nodeId } returns "node-2"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(MinionsAssignmentState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            verifyNever { campaign.unassignFactory("node-2") }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

}