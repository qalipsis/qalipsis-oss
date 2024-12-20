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
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisFailureStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    internal fun `should be a completion state`() {
        assertThat(RedisFailureState(campaign, "", operations).isCompleted).isFalse()
    }

    @Test
    internal fun `should return shutdown directive on init`() = testDispatcherProvider.run {
        // given
        val campaign = campaign.copy()
        campaign.broadcastChannel = "my-broadcast-channel"
        campaign.feedbackChannel = "my-feedback-channel"
        campaign.factories += mapOf(
            "node-1" to FactoryConfiguration(
                "", mutableMapOf(
                    "scenario-1" to FactoryScenarioAssignment("scenario-1", emptyList()),
                    "scenario-2" to FactoryScenarioAssignment("scenario-2", emptyList())
                )
            ),
            "node-2" to FactoryConfiguration(
                "", mutableMapOf(
                    "scenario-2" to FactoryScenarioAssignment("scenario-2", emptyList())
                )
            )
        )

        // when
        val directives = RedisFailureState(campaign, "this error", operations).run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(campaign.message).isEqualTo("this error")
        assertThat(directives).all {
            hasSize(1)
            containsOnly(
                CampaignShutdownDirective("my-campaign", "my-broadcast-channel")
            )
        }
        assertThat(operations.getState(campaign.tenant, campaign.key)).isNotNull().all {
            prop(Pair<RunningCampaign, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<RunningCampaign, CampaignRedisState>::second).isEqualTo(CampaignRedisState.FAILURE_STATE)
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should unassign when feedback is ignored then MinionsAssignmentState when all the feedbacks were received`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk(),
                "node-2" to relaxedMockk()
            )
            var state = RedisFailureState(campaign, "this error", operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            var newState = state.process(mockk<CampaignShutdownFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameAs(state)

            // when
            state = RedisFailureState(campaign, operations)
            state.initialized = true
            assertThat(state.run {
                inject(campaignExecutionContext)
                init()
            }).isEmpty()
            newState = state.process(mockk<CampaignShutdownFeedback> {
                every { nodeId } returns "node-2"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(RedisDisabledState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("isSuccessful").isFalse()
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce {
                campaignReportStateKeeper.complete("my-campaign", ExecutionStatus.FAILED, "this error")
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED, "this error")
            }
            confirmVerified(campaignService, factoryService, campaignReportStateKeeper)
        }
}