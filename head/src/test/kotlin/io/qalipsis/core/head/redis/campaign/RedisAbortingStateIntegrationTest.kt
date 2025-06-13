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
import assertk.assertions.isSameInstanceAs
import assertk.assertions.key
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.NodeExecutionFeedback
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisAbortingStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisAbortingState(campaign, AbortRunningCampaign(), "", operations).isCompleted).isFalse()
    }

    @Test
    internal fun `should return campaign abort directive on init`() = testDispatcherProvider.run {
        // given
        every { campaign.scenarios } returns mapOf(
            "scenario-1" to relaxedMockk { every { minionsCount } returns 54 },
            "scenario-2" to relaxedMockk { every { minionsCount } returns 43 }
        )

        // when
        val directives = RedisAbortingState(campaign, AbortRunningCampaign(), "Aborting campaign", operations).run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(campaign.message).isEqualTo("Aborting campaign")
        assertThat(directives).all {
            hasSize(1)
            containsOnly(
                CampaignAbortDirective("my-campaign", "my-broadcast-channel", listOf("scenario-1", "scenario-2"))
            )
        }
        assertThat(operations.getState(campaign.tenant, campaign.key)).isNotNull().all {
            prop(Pair<RunningCampaign, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<RunningCampaign, CampaignRedisState>::second).isEqualTo(CampaignRedisState.ABORTING_STATE)
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a new RedisFailureState when a CampaignAbortFeedback and all the feedbacks were received and the abort is hard`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf("node-1" to relaxedMockk())

            val state = RedisAbortingState(
                campaign,
                AbortRunningCampaign(hard = true),
                "The campaign was aborted",
                operations
            )
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<CampaignAbortFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.FAILED
            })

            // then
            assertThat(newState).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("error").isSameInstanceAs("The campaign was aborted")
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce {
                campaignReportStateKeeper.complete("my-campaign", ExecutionStatus.ABORTED, "The campaign was aborted")
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.ABORTED, "The campaign was aborted")
            }
            confirmVerified(factoryService, campaignService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisCompletionState when a CampaignAbortFeedback and all the feedbacks were received and the abort is soft`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf("node-1" to relaxedMockk())

            val state = RedisAbortingState(
                campaign,
                AbortRunningCampaign(hard = false),
                "The campaign was aborted",
                operations
            )
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
            assertThat(newState).isInstanceOf(RedisCompletionState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            confirmVerified(campaignService, factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisAbortingState`() = testDispatcherProvider.run {
        // given
        val state = RedisAbortingState(
            campaign,
            AbortRunningCampaign(true),
            "The campaign was aborted",
            operations
        )
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        val newState = state.abort(AbortRunningCampaign())

        // then
        assertThat(newState).isInstanceOf(RedisAbortingState::class).all {
            prop("campaign").isSameInstanceAs(campaign)
            prop("error").isSameInstanceAs("The campaign was aborted")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return the same state when a node cannot be executed and other feedback are expected`() =
        testDispatcherProvider.run {
            // given
            campaign.factories.put("node-3", FactoryConfiguration("node-3-channel"))
            val state = RedisAbortingState(
                campaign,
                AbortRunningCampaign(true),
                "The campaign was aborted",
                operations
            )
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val feedback = mockk<NodeExecutionFeedback> {
                every { nodeId } returns "node-2"
                every { status.isDone } returns true
                every { error } returns "this is the error"
            }

            // when
            val newState = state.process(feedback)

            // then
            assertThat(campaign.factories).all {
                hasSize(2)
                key("node-1").isNotNull()
                key("node-3").isNotNull()
            }
            assertThat(newState).isSameInstanceAs(state)
            confirmVerified(factoryService, campaignService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return the failure state when a node cannot be executed and all other feedbacks were received`() =
        testDispatcherProvider.run {
            // given
            campaign.factories.put("node-3", FactoryConfiguration("node-3-channel"))
            var state = RedisAbortingState(
                campaign,
                AbortRunningCampaign(true),
                "The campaign was aborted",
                operations
            )
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            var newState = state.process(mockk<CampaignAbortFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameInstanceAs(state)

            // when
            state = RedisAbortingState(
                campaign,
                AbortRunningCampaign(true),
                "The campaign was aborted",
                operations
            )
            state.initialized = true
            assertThat(state.run {
                inject(campaignExecutionContext)
                init()
            }).isEmpty()
            newState = state.process(mockk<CampaignAbortFeedback> {
                every { nodeId } returns "node-3"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isSameInstanceAs(state)

            // when
            state = RedisAbortingState(
                campaign,
                AbortRunningCampaign(true),
                "The campaign was aborted",
                operations
            )
            state.initialized = true
            assertThat(state.run {
                inject(campaignExecutionContext)
                init()
            }).isEmpty()
            val feedback = mockk<NodeExecutionFeedback> {
                every { nodeId } returns "node-2"
                every { status.isDone } returns true
                every { error } returns "this is the error"
            }
            newState = state.process(feedback)

            // then
            assertThat(campaign.factories).all {
                hasSize(2)
                key("node-1").isNotNull()
                key("node-3").isNotNull()
            }
            assertThat(newState).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("error").isSameInstanceAs("The campaign was aborted")
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce {
                campaignReportStateKeeper.complete("my-campaign", ExecutionStatus.ABORTED, "The campaign was aborted")
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.ABORTED, "The campaign was aborted")
            }
            confirmVerified(factoryService, campaignService, campaignReportStateKeeper)
        }
}