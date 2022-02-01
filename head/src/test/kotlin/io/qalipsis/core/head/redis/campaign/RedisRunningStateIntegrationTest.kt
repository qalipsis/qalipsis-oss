package io.qalipsis.core.head.redis.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.feedbacks.CompleteMinionFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FailedCampaignFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisRunningStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisRunningState(campaign, operations).isCompleted).isFalse()
    }

    @Test
    fun `should return the directives passed as parameter on init`() = testDispatcherProvider.run {
        // given
        val initDirectives = listOf<Directive>(relaxedMockk(), relaxedMockk())
        val state = RedisRunningState(campaign, operations, false, initDirectives)
        operations.saveConfiguration(campaign)

        // when
        val directives = state.init(factoryService, campaignReportStateKeeper, idGenerator)

        // then
        assertThat(directives).isSameAs(initDirectives)

        assertThat(operations.getState(campaign.id)).isNotNull().all {
            prop(Pair<CampaignConfiguration, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<CampaignConfiguration, CampaignRedisState>::second).isEqualTo(CampaignRedisState.RUNNING_STATE)
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    fun `should return the directives passed as parameter on init but not persist the state`() =
        testDispatcherProvider.run {
            // given
            val initDirectives = listOf<Directive>(relaxedMockk(), relaxedMockk())
            val state = RedisRunningState(campaign, operations, true, initDirectives)
            operations.saveConfiguration(campaign)

            // when
            val directives = state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // then
            assertThat(directives).isSameAs(initDirectives)

            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.run {
        // given
        val state = RedisRunningState(campaign, operations)
        state.init(factoryService, campaignReportStateKeeper, idGenerator)

        // when
        var newState = state.process(mockk<MinionsDeclarationFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 1"
        })

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
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
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
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
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 3")
        }

        // when
        newState = state.process(mockk<FailedCampaignFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 4"
        })

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 4")
        }

        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)
            val feedback = mockk<MinionsStartFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.FAILED
                every { error } returns null
            }

            // when
            val newState = state.process(feedback)

            // then
            assertThat(newState).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("error").isEqualTo("")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return itself in case of any unsupported feedback`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // when
            val newState = state.process(mockk<Feedback>())

            // then
            assertThat(newState).isSameAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return itself in case of any unsupported directive`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // when
            val newState = state.process(mockk<Directive>())

            // then
            assertThat(newState).isSameAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisRunningState with a MinionsShutdownDirective when a minion is complete`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // when
            val newState = state.process(mockk<CompleteMinionFeedback> {
                every { scenarioId } returns "the scenario"
                every { minionId } returns "the minion"
            })

            // then
            assertThat(newState).isInstanceOf(RedisRunningState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("doNotPersistStateOnInit").isTrue()
                typedProp<Boolean>("initialized").isFalse()
                typedProp<Collection<Directive>>("directivesForInit").all {
                    hasSize(1)
                    containsOnly(
                        MinionsShutdownDirective(
                            "my-campaign",
                            "the scenario",
                            listOf("the minion"),
                            "the-directive-1",
                            "my-broadcast-channel"
                        )
                    )
                }
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisRunningState with a CampaignScenarioShutdownDirective when a scenario is complete`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // when
            val newState = state.process(mockk<EndOfCampaignScenarioFeedback> {
                every { campaignId } returns "my-campaign"
                every { scenarioId } returns "the scenario"
            })

            // then
            assertThat(newState).isInstanceOf(RedisRunningState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("doNotPersistStateOnInit").isTrue()
                typedProp<Boolean>("initialized").isFalse()
                typedProp<Collection<Directive>>("directivesForInit").all {
                    hasSize(1)
                    containsOnly(
                        CampaignScenarioShutdownDirective(
                            "my-campaign",
                            "the scenario",
                            "the-directive-1",
                            "my-broadcast-channel"
                        )
                    )
                }
            }
            coVerifyOnce { campaignReportStateKeeper.complete("my-campaign", "the scenario") }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisCompletionState with a EndOfCampaignFeedback when a scenario is complete`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

            // when
            val newState = state.process(mockk<EndOfCampaignFeedback> {
                every { campaignId } returns "my-campaign"
            })

            // then
            assertThat(newState).isInstanceOf(RedisCompletionState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce { campaignReportStateKeeper.complete("my-campaign") }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }
}