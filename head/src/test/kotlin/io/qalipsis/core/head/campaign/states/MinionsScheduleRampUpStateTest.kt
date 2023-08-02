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

package io.qalipsis.core.head.campaign.states

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.executionprofile.CompletionMode.HARD
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.head.factory.FactoryHealth
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import org.junit.jupiter.api.Test
import java.time.Instant

internal class MinionsScheduleRampUpStateTest : AbstractStateTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(MinionsScheduleRampUpState(campaign).isCompleted).isFalse()
    }

    @Test
    fun `should return the directives for profile for each scenario`() = testDispatcherProvider.runTest {
        // given
        val state = MinionsScheduleRampUpState(campaign)
        every { campaign.scenarios } returns mapOf(
            "scenario-1" to ScenarioConfiguration(
                minionsCount = 54,
                DefaultExecutionProfileConfiguration()
            ),
            "scenario-2" to ScenarioConfiguration(
                minionsCount = 255,
                StageExecutionProfileConfiguration(
                    completion = HARD, stages = listOf(
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
                        HARD,
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
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.runTest {
        // given
        val state = MinionsScheduleRampUpState(campaign)
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

        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.runTest {
            // given
            val state = MinionsScheduleRampUpState(campaign)
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
            val state = MinionsScheduleRampUpState(campaign)
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
    internal fun `should return a new WarmupState when all MinionsRampUpPreparationFeedback are received`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.scenarios } returns mapOf(
                "scenario-1" to mockk {
                    every { executionProfileConfiguration.clone() } returns mockk()
                },
                "scenario-2" to mockk {
                    every { executionProfileConfiguration.clone() } returns mockk()
                }
            )
            val state = MinionsScheduleRampUpState(campaign)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
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
            assertThat(newState).isInstanceOf(WarmupState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    fun `should return a disabled state when no factory can be found`() = testDispatcherProvider.runTest {
        // given
        val state = MinionsScheduleRampUpState(campaign)
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
    fun `should return a aborting state when some factories are unhealthy`() = testDispatcherProvider.runTest {
        // given
        val state = MinionsScheduleRampUpState(campaign)
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
        assertThat(newState).isInstanceOf(AbortingState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("abortConfiguration").isSameAs(abortRunningCampaign)
            prop("error").isSameAs("The campaign was aborted")
        }
        assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1"))
        coVerifyOrder {
            factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1"))
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    fun `should return an aborting state when all factories are healthy`() = testDispatcherProvider.runTest {
        // given
        val state = MinionsScheduleRampUpState(campaign)
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
        assertThat(newState).isInstanceOf(AbortingState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("abortConfiguration").isSameAs(abortRunningCampaign)
            prop("error").isSameAs("The campaign was aborted")
        }
        assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1", "node-2", "node-3"))
        coVerifyOrder {
            factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1", "node-2", "node-3"))
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    fun `should return a disabled state when all factories are unhealthy`() = testDispatcherProvider.runTest {
        // given
        val state = MinionsScheduleRampUpState(campaign)
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