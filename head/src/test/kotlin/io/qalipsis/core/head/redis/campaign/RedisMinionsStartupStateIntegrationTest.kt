/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.redis.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.executionprofile.CompletionMode
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisMinionsStartupStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisMinionsStartupState(campaign, operations).isCompleted).isFalse()
    }

    @Test
    fun `should return the directives for profile for each scenario`() = testDispatcherProvider.run {
        // given
        val runningCampaign = campaign.copy(
            startOffsetMs = 1234L,
            speedFactor = 153.42,
            scenarios = mapOf(
                "scenario-1" to ScenarioConfiguration(
                    minionsCount = 54,
                    DefaultExecutionProfileConfiguration()
                ),
                "scenario-2" to ScenarioConfiguration(
                    minionsCount = 255,
                    StageExecutionProfileConfiguration(
                        stages = listOf(
                            Stage(
                                minionsCount = 123,
                                rampUpDurationMs = 234,
                                totalDurationMs = 34534,
                                resolutionMs = 6454
                            ),
                            Stage(
                                minionsCount = 463,
                                rampUpDurationMs = 3245,
                                totalDurationMs = 6453454,
                                resolutionMs = 234
                            )
                        ), completion = CompletionMode.FORCED
                    )
                )
            )
        ).also {
            it.broadcastChannel = "my-broadcast-channel"
            it.feedbackChannel = "my-feedback-channel"
        }
        val state = RedisMinionsStartupState(runningCampaign, operations)
        operations.saveConfiguration(runningCampaign)

        // when
        val directives = state.run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(directives).all {
            hasSize(2)
            containsOnly(
                MinionsRampUpPreparationDirective(
                    "my-campaign",
                    "scenario-1",
                    DefaultExecutionProfileConfiguration(1234L, 153.42),
                    "my-broadcast-channel"
                ),
                MinionsRampUpPreparationDirective(
                    "my-campaign",
                    "scenario-2",
                    StageExecutionProfileConfiguration(
                        stages = listOf(
                            Stage(
                                minionsCount = 123,
                                rampUpDurationMs = 234,
                                totalDurationMs = 34534,
                                resolutionMs = 6454
                            ),
                            Stage(
                                minionsCount = 463,
                                rampUpDurationMs = 3245,
                                totalDurationMs = 6453454,
                                resolutionMs = 234
                            )
                        ),
                        completion = CompletionMode.FORCED,
                        startOffsetMs = 1234L, speedFactor = 153.42
                    ),
                    "my-broadcast-channel"
                )
            )
        }
        assertThat(operations.getState(campaign.tenant, campaign.key)).isNotNull().all {
            prop(Pair<RunningCampaign, CampaignRedisState>::first).isDataClassEqualTo(runningCampaign)
            prop(Pair<RunningCampaign, CampaignRedisState>::second).isEqualTo(CampaignRedisState.MINIONS_STARTUP_STATE)
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.run {
        // given
        val runningCampaign = campaign.copy().also { it.broadcastChannel = "my-broadcast-channel" }
        val state = RedisMinionsStartupState(runningCampaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        var newState = state.process(mockk<MinionsDeclarationFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 1"
        })

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameAs(runningCampaign)
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
            prop("campaign").isSameAs(runningCampaign)
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
            prop("campaign").isSameAs(runningCampaign)
            prop("error").isEqualTo("this is the error 3")
        }

        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.run {
            // given
            val runningCampaign = campaign.copy().also { it.broadcastChannel = "my-broadcast-channel" }
            val state = RedisMinionsStartupState(runningCampaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val feedback = mockk<MinionsStartFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.FAILED
                every { error } returns null
            }

            // when
            val newState = state.process(feedback)

            // then
            assertThat(newState).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameAs(runningCampaign)
                prop("error").isEqualTo("")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return itself in case of any unsupported feedback`() =
        testDispatcherProvider.run {
            // given
            val runningCampaign = campaign.copy().also { it.broadcastChannel = "my-broadcast-channel" }
            val state = RedisMinionsStartupState(runningCampaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk())

            // then
            assertThat(newState).isSameAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisRunningState when a completed MinionsStartFeedback is received`() =
        testDispatcherProvider.run {
            // given
            val runningCampaign = campaign.copy().also { it.broadcastChannel = "my-broadcast-channel" }
            val state = RedisMinionsStartupState(runningCampaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState =
                state.process(mockk<MinionsStartFeedback> { every { status } returns FeedbackStatus.COMPLETED })

            // then
            assertThat(newState).isInstanceOf(RedisRunningState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameAs(runningCampaign)
                typedProp<Boolean>("initialized").isFalse()
                typedProp<Collection<Directive>>("directivesForInit").isEmpty()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    fun `should return a new RedisAbortingState`() = testDispatcherProvider.run {
        // given
        val state = RedisMinionsStartupState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        val newState = state.abort(AbortRunningCampaign())

        // then
        assertThat(newState).isInstanceOf(RedisAbortingState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isSameAs("The campaign was aborted")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }
}