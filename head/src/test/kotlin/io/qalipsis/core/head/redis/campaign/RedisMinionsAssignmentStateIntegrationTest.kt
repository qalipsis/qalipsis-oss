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
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisMinionsAssignmentStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisMinionsAssignmentState(campaign, operations).isCompleted).isFalse()
    }

    @Test
    fun `should return minions declaration directives on init`() = testDispatcherProvider.run {
        // given
        every { campaign.scenarios } returns mapOf(
            "scenario-1" to relaxedMockk { every { minionsCount } returns 54 },
            "scenario-2" to relaxedMockk { every { minionsCount } returns 43 }
        )
        every { campaign.factories } returns mutableMapOf(
            "node-1" to relaxedMockk {
                every { assignment } returns mutableMapOf(
                    "scenario-1" to relaxedMockk(),
                    "scenario-2" to relaxedMockk()
                )
            },
            "node-2" to relaxedMockk {
                every { assignment } returns mutableMapOf("scenario-2" to relaxedMockk())
            }
        )
        operations.saveConfiguration(campaign)
        val state = RedisMinionsAssignmentState(campaign, operations)

        // when
        val directives = state.run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(directives).all {
            hasSize(2)
            containsOnly(
                MinionsDeclarationDirective(
                    "my-campaign", "scenario-1", 54, "my-broadcast-channel"
                ),
                MinionsDeclarationDirective(
                    "my-campaign", "scenario-2", 43, "my-broadcast-channel"
                )
            )
        }
        assertThat(operations.getState(campaign.tenant, campaign.key)).isNotNull().all {
            prop(Pair<RunningCampaign, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<RunningCampaign, CampaignRedisState>::second).isEqualTo(CampaignRedisState.MINIONS_ASSIGNMENT_STATE)
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.run {
        // given
        val state = RedisMinionsAssignmentState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }
        val feedback = mockk<MinionsDeclarationFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error"
        }

        // when
        val newState = state.process(feedback)

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.run {
            // given
            val state = RedisMinionsAssignmentState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val feedback = mockk<MinionsAssignmentFeedback> {
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
            val state = RedisMinionsAssignmentState(campaign, operations)
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
    internal fun `should unassign and release factory when feedback is ignored and no more scenarios are assigned to factory`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-1" to relaxedMockk(),
                        "scenario-2" to relaxedMockk()
                    )
                },
                "node-2" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-2" to relaxedMockk()
                    )
                }
            )
            val state = RedisMinionsAssignmentState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            every { campaign.contains("node-1") } returns false

            // when
            val newState = state.process(mockk<MinionsAssignmentFeedback> {
                every { nodeId } returns "node-1"
                every { scenarioName } returns "scenario-2"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameAs(state)
            coVerifyOrder {
                campaign.unassignScenarioOfFactory("scenario-2", "node-1")
                campaign.contains("node-1")
                factoryService.releaseFactories(refEq(campaign), listOf("node-1"))
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should unassign but not release factory when feedback is ignored and more scenarios are assigned to factory`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-1" to relaxedMockk(),
                        "scenario-2" to relaxedMockk()
                    )
                },
                "node-2" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-2" to relaxedMockk()
                    )
                }
            )
            val state = RedisMinionsAssignmentState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            every { campaign.contains("node-1") } returns true

            // when
            val newState = state.process(mockk<MinionsAssignmentFeedback> {
                every { nodeId } returns "node-1"
                every { scenarioName } returns "scenario-2"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameAs(state)
            coVerifyOrder {
                campaign.unassignScenarioOfFactory("scenario-2", "node-1")
                campaign.contains("node-1")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return RedisWarmupState when all the declaration feedbacks were received`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-1" to relaxedMockk(),
                        "scenario-2" to relaxedMockk()
                    )
                },
                "node-2" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-2" to relaxedMockk()
                    )
                }
            )
            every { campaign.contains(any()) } returns true
            var state = RedisMinionsAssignmentState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            var newState = state.process(mockk<MinionsAssignmentFeedback> {
                every { nodeId } returns "node-1"
                every { scenarioName } returns "scenario-2"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameAs(state)

            // when
            state = RedisMinionsAssignmentState(campaign, operations)
            state.initialized = true
            assertThat(state.run {
                inject(campaignExecutionContext)
                init()
            }).isEmpty()
            newState = state.process(mockk<MinionsAssignmentFeedback> {
                every { nodeId } returns "node-2"
                every { scenarioName } returns "scenario-2"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isSameAs(state)

            // when
            state = RedisMinionsAssignmentState(campaign, operations)
            state.initialized = true
            assertThat(state.run {
                inject(campaignExecutionContext)
                init()
            }).isEmpty()
            newState = state.process(mockk<MinionsAssignmentFeedback> {
                every { nodeId } returns "node-1"
                every { scenarioName } returns "scenario-1"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(RedisWarmupState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            verifyOnce { campaign.unassignScenarioOfFactory(any(), any()) }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    fun `should return a new RedisAbortingState`() = testDispatcherProvider.run {
        // given
        val state = RedisMinionsAssignmentState(campaign, operations)
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