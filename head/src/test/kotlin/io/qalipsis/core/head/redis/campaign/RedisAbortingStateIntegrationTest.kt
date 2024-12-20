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
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
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
                prop("campaign").isSameAs(campaign)
                prop("error").isSameAs("The campaign was aborted")
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce {
                campaignReportStateKeeper.complete("my-campaign", ExecutionStatus.ABORTED, "The campaign was aborted")
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.ABORTED, "The campaign was aborted")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
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
                prop("campaign").isSameAs(campaign)
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
            prop("campaign").isSameAs(campaign)
            prop("error").isSameAs("The campaign was aborted")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }
}