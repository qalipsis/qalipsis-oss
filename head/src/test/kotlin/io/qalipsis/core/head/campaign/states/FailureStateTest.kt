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
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class FailureStateTest : AbstractStateTest() {

    @Test
    internal fun `should be a completion state`() {
        assertThat(FailureState(campaign, "").isCompleted).isFalse()
    }

    @Test
    internal fun `should return shutdown directive on init`() = testDispatcherProvider.runTest {
        // when
        val directives = FailureState(campaign, "this error").run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        verify { campaign setProperty "message" value "this error" }
        assertThat(directives).all {
            hasSize(1)
            containsOnly(
                CampaignShutdownDirective("my-campaign", "my-broadcast-channel")
            )
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return DisabledState when all the feedbacks were received`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk(),
                "node-2" to relaxedMockk()
            )
            val state = FailureState(campaign, "this error")
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
            assertThat(newState).all {
                isSameAs(state)
                typedProp<Collection<NodeId>>("expectedFeedbacks").containsOnly("node-2")
            }

            // when
            newState = state.process(mockk<CampaignShutdownFeedback> {
                every { nodeId } returns "node-2"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(DisabledState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("isSuccessful").isFalse()
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce {
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED)
            }
            confirmVerified(campaignService, factoryService, campaignReportStateKeeper)
        }
}