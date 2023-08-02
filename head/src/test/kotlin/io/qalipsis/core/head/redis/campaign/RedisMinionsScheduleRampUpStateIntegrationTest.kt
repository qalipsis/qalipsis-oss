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
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.executionprofile.CompletionMode
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.head.campaign.states.DisabledState
import io.qalipsis.core.head.factory.FactoryHealth
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import org.junit.jupiter.api.Test
import java.time.Instant

@ExperimentalLettuceCoroutinesApi
internal class RedisMinionsScheduleRampUpStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisMinionsScheduleRampUpState(campaign, operations).isCompleted).isFalse()
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
                        completion = CompletionMode.HARD, stages = listOf(
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
                        )
                    )
                )
            )
        ).also {
            it.broadcastChannel = "my-broadcast-channel"
            it.feedbackChannel = "my-feedback-channel"
        }
        val state = RedisMinionsScheduleRampUpState(runningCampaign, operations)

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
                    DefaultExecutionProfileConfiguration(),
                    "my-broadcast-channel"
                ),
                MinionsRampUpPreparationDirective(
                    "my-campaign",
                    "scenario-2",
                    StageExecutionProfileConfiguration(
                        CompletionMode.HARD,
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
        val runningCampaign = campaign.copy().also {
            it.feedbackChannel = "my-feedback-channel"
            it.broadcastChannel = "my-broadcast-channel"
        }
        val state = RedisMinionsScheduleRampUpState(runningCampaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        var newState = state.process(mockk<MinionsRampUpPreparationFeedback> {
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

        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.run {
            // given
            val runningCampaign = campaign.copy().also {
                it.feedbackChannel = "my-feedback-channel"
                it.broadcastChannel = "my-broadcast-channel"
            }
            val state = RedisMinionsScheduleRampUpState(runningCampaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val feedback = mockk<MinionsRampUpPreparationFeedback> {
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
            val runningCampaign = campaign.copy().also {
                it.feedbackChannel = "my-feedback-channel"
                it.broadcastChannel = "my-broadcast-channel"
            }
            val state = RedisMinionsScheduleRampUpState(runningCampaign, operations)
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
    internal fun `should return a new RedisWarmupState when a completed MinionsStartFeedback is received`() =
        testDispatcherProvider.run {
            // given
            val runningCampaign = campaign.copy(
                scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(123, DefaultExecutionProfileConfiguration()),
                    "scenario-2" to ScenarioConfiguration(123, DefaultExecutionProfileConfiguration())
                )
            ).also {
                it.feedbackChannel = "my-feedback-channel"
                it.broadcastChannel = "my-broadcast-channel"
            }
            val state = RedisMinionsScheduleRampUpState(runningCampaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            var newState =
                state.process(mockk<MinionsRampUpPreparationFeedback> {
                    every { scenarioName } returns "scenario-1"
                    every { status } returns FeedbackStatus.COMPLETED
                })

            // then
            assertThat(newState).isSameAs(state)

            // when
            newState =
                state.process(mockk<MinionsRampUpPreparationFeedback> {
                    every { scenarioName } returns "scenario-2"
                    every { status } returns FeedbackStatus.COMPLETED
                })

            // then
            assertThat(newState).isInstanceOf(RedisWarmupState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameAs(runningCampaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    fun `should return a new redis disabled state when no factory can be found`() = testDispatcherProvider.run {
        // given
        val state = RedisMinionsScheduleRampUpState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }
        every { campaign.factories.keys } returns mutableSetOf("node-1", "node-2", "node-3")
        coEvery {
            factoryService.getFactoriesHealth(
                "my-tenant",
                mutableSetOf("node-1", "node-2", "node-3")
            )
        } returns setOf()

        // when
        val newState = state.abort(AbortRunningCampaign())

        // then
        assertThat(newState).isInstanceOf(DisabledState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("isSuccessful").isSameAs(false)
        }
        coVerifyOrder {
            factoryService.getFactoriesHealth(refEq("my-tenant"), mutableSetOf("node-1", "node-2", "node-3"))
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    fun `should return a new redis aborting state when some factories are unhealthy`() = testDispatcherProvider.run {
        // given
        val state = RedisMinionsScheduleRampUpState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }
        val instant = Instant.now()
        val abortRunningCampaign = AbortRunningCampaign()

        every { campaign.factories.keys } returns mutableSetOf("node-1", "node-2", "node-3")
        coEvery {
            factoryService.getFactoriesHealth(
                "my-tenant",
                mutableSetOf("node-1", "node-2", "node-3")
            )
        } returns setOf(
            FactoryHealth("node-1", instant, Heartbeat.State.IDLE),
            FactoryHealth("node-2", instant, Heartbeat.State.OFFLINE),
            FactoryHealth("node-3", instant, Heartbeat.State.UNHEALTHY)
        )

        // when
        val newState = state.abort(abortRunningCampaign)

        // then
        assertThat(newState).isInstanceOf(RedisAbortingState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("abortConfiguration").isSameAs(abortRunningCampaign)
            prop("error").isSameAs("The campaign was aborted")
            prop("operations").isSameAs(operations)
        }
        assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1"))
        coVerifyOrder {
            factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1"))
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a new redis aborting state when all factories are healthy`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val instant = Instant.now()
            val abortRunningCampaign = AbortRunningCampaign()

            every { campaign.factories.keys } returns mutableSetOf("node-1", "node-2", "node-3")
            coEvery {
                factoryService.getFactoriesHealth(
                    "my-tenant",
                    mutableSetOf("node-1", "node-2", "node-3")
                )
            } returns setOf(
                FactoryHealth("node-1", instant, Heartbeat.State.IDLE),
                FactoryHealth("node-2", instant, Heartbeat.State.IDLE),
                FactoryHealth("node-3", instant, Heartbeat.State.IDLE)
            )

            // when
            val newState = state.abort(abortRunningCampaign)

            // then
            assertThat(newState).isInstanceOf(RedisAbortingState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("abortConfiguration").isSameAs(abortRunningCampaign)
                prop("error").isSameAs("The campaign was aborted")
                prop("operations").isSameAs(operations)
            }
            assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1", "node-2", "node-3"))
            coVerifyOrder {
                factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1", "node-2", "node-3"))
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new redis aborting state when all factories are unhealthy`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val instant = Instant.now()
            val abortRunningCampaign = AbortRunningCampaign()

            every { campaign.factories.keys } returns mutableSetOf("node-1", "node-2", "node-3")
            coEvery {
                factoryService.getFactoriesHealth(
                    "my-tenant",
                    mutableSetOf("node-1", "node-2", "node-3")
                )
            } returns setOf(
                FactoryHealth("node-1", instant, Heartbeat.State.REGISTERED),
                FactoryHealth("node-2", instant, Heartbeat.State.OFFLINE),
                FactoryHealth("node-3", instant, Heartbeat.State.UNHEALTHY)
            )

            // when
            val newState = state.abort(abortRunningCampaign)

            // then
            assertThat(newState).isInstanceOf(DisabledState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("isSuccessful").isSameAs(false)
            }
            assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1", "node-2", "node-3"))
            coVerifyOrder {
                factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1", "node-2", "node-3"))
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }
}