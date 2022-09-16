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
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisDisabledStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    internal fun `should be a completion state`() {
        assertThat(RedisDisabledState(campaign, true, operations).isCompleted).isTrue()
    }

    @Test
    internal fun `should return CompleteCampaignDirective with success on init and publish with all publishers despite error`() =
        testDispatcherProvider.run {
            // given
            operations.saveConfiguration(campaign)
            operations.setState(campaign.tenant, campaign.key, CampaignRedisState.COMPLETION_STATE)
            operations.prepareAssignmentsForFeedbackExpectations(campaign)
            val report = relaxedMockk<CampaignReport>()
            coEvery { campaignReportStateKeeper.generateReport(any()) } returns report
            coEvery { reportPublisher1.publish(any(), any()) } throws RuntimeException()

            // when
            every { campaign.message } returns "this is a message"
            val directives = RedisDisabledState(campaign, true, operations).run {
                inject(campaignExecutionContext)
                init()
            }

            // then
            assertThat(directives).all {
                hasSize(1)
                containsOnly(
                    CompleteCampaignDirective(
                        "my-campaign",
                        true,
                        "this is a message",
                        "my-broadcast-channel"
                    )
                )
            }
            assertThat(connection.sync().keys("*").count()).isEqualTo(0)
            coVerifyOrder {
                factoryService.releaseFactories(refEq(campaign), setOf("node-1", "node-2"))
                headChannel.unsubscribeFeedback("my-feedback-channel")
                campaignReportStateKeeper.generateReport("my-campaign")
                reportPublisher1.publish("my-campaign", refEq(report))
                reportPublisher2.publish("my-campaign", refEq(report))
                campaignAutoStarter.completeCampaign(refEq(directives.first() as CompleteCampaignDirective))
            }
            confirmVerified(factoryService, campaignReportStateKeeper, campaignAutoStarter)
        }

    @Test
    internal fun `should return CompleteCampaignDirective with failure on init and publish`() =
        testDispatcherProvider.run {
            // given
            operations.saveConfiguration(campaign)
            operations.setState(campaign.tenant, campaign.key, CampaignRedisState.FAILURE_STATE)
            operations.prepareAssignmentsForFeedbackExpectations(campaign)
            val report = relaxedMockk<CampaignReport>()
            coEvery { campaignReportStateKeeper.generateReport(any()) } returns report
            // when
            every { campaign.message } returns "this is a message"
            val directives = RedisDisabledState(campaign, false, operations).run {
                inject(campaignExecutionContext)
                init()
            }

            // then
            assertThat(directives).all {
                hasSize(1)
                containsOnly(
                    CompleteCampaignDirective(
                        "my-campaign",
                        false,
                        "this is a message",
                        "my-broadcast-channel"
                    )
                )
            }
            coVerifyOrder {
                factoryService.releaseFactories(refEq(campaign), setOf("node-1", "node-2"))
                headChannel.unsubscribeFeedback("my-feedback-channel")
                campaignReportStateKeeper.generateReport("my-campaign")
                reportPublisher1.publish("my-campaign", refEq(report))
                campaignAutoStarter.completeCampaign(refEq(directives.first() as CompleteCampaignDirective))
            }
            assertThat(connection.sync().keys("*").count()).isEqualTo(0)
            confirmVerified(factoryService, campaignReportStateKeeper, campaignAutoStarter)
        }
}