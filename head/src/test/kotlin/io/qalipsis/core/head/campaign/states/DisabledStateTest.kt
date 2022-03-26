package io.qalipsis.core.head.campaign.states

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class DisabledStateTest : AbstractStateTest() {

    @Test
    internal fun `should be a completion state`() {
        assertThat(DisabledState(campaign).isCompleted).isTrue()
    }

    @Test
    internal fun `should return CompleteCampaignDirective with success on init and publish with all publishers despite error`() =
        testDispatcherProvider.runTest {
            // when
            val report = relaxedMockk<CampaignReport>()
            coEvery { campaignReportStateKeeper.report(any()) } returns report
            coEvery { reportPublisher1.publish(any(), any()) } throws RuntimeException()
            every { campaign.message } returns "this is a message"
            val directives = DisabledState(campaign, true).run {
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
            coVerifyOrder {
                headChannel.unsubscribeFeedback("my-feedback-channel")
                campaignReportStateKeeper.report("my-campaign")
                reportPublisher1.publish(refEq(campaign), refEq(report))
                reportPublisher2.publish(refEq(campaign), refEq(report))
                campaignAutoStarter.completeCampaign(refEq(directives.first() as CompleteCampaignDirective))
            }
            confirmVerified(factoryService, campaignReportStateKeeper, campaignAutoStarter)
        }

    @Test
    internal fun `should return CompleteCampaignDirective with failure on init and publish`() =
        testDispatcherProvider.runTest {
            // when
            val report = relaxedMockk<CampaignReport>()
            coEvery { campaignReportStateKeeper.report(any()) } returns report
            every { campaign.message } returns "this is a message"
            val directives = DisabledState(campaign, false).run {
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
                headChannel.unsubscribeFeedback("my-feedback-channel")
                campaignReportStateKeeper.report("my-campaign")
                reportPublisher1.publish(refEq(campaign), refEq(report))
                reportPublisher2.publish(refEq(campaign), refEq(report))
                campaignAutoStarter.completeCampaign(refEq(directives.first() as CompleteCampaignDirective))
            }
            confirmVerified(factoryService, campaignReportStateKeeper, campaignAutoStarter)
        }
}