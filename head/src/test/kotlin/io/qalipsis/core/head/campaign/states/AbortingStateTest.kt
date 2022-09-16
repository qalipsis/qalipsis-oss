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
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class AbortingStateTest : AbstractStateTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(AbortingState(campaign, AbortRunningCampaign(), "").isCompleted).isFalse()
    }

    @Test
    fun `should return campaign abort directive on init`() = testDispatcherProvider.runTest {
        // given
        every { campaign.scenarios } returns mapOf(
            "scenario-1" to ScenarioConfiguration(2),
            "scenario-2" to ScenarioConfiguration(5)
        )

        // when
        val directives = AbortingState(campaign, AbortRunningCampaign(), "").run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(directives).all {
            hasSize(1)
            containsOnly(
                CampaignAbortDirective(
                    "my-campaign",
                    "my-broadcast-channel",
                    listOf("scenario-1", "scenario-2")
                )
            )
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a new CompletionState when a CampaignAbortFeedback and all the feedbacks were received and the abort is soft`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf("node-1" to relaxedMockk(),)

            val state = AbortingState(campaign, AbortRunningCampaign(hard = false), "")
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
            assertThat(newState).isInstanceOf(CompletionState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            confirmVerified(campaignService, factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new FailureState when a CampaignAbortFeedback and all the feedbacks were received and the abort is hard`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf("node-1" to relaxedMockk())

            val state = AbortingState(campaign, AbortRunningCampaign(hard = true), "")
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
            assertThat(newState).isInstanceOf(FailureState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("error").isSameAs("The campaign was aborted")
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce {
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.ABORTED)
            }
            confirmVerified(campaignService, factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new AbortingState when a CampaignAbortFeedback was received`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk(),
                "node-2" to relaxedMockk()
            )

            val state = AbortingState(campaign, AbortRunningCampaign(), "this error")
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
            assertThat(newState).isInstanceOf(AbortingState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("error").isSameAs("this error")
                typedProp<Boolean>("initialized").isTrue()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }
}