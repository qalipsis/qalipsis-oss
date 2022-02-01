package io.qalipsis.core.head.campaign.states

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isTrue
import io.mockk.confirmVerified
import io.mockk.every
import io.qalipsis.core.directives.CompleteCampaignDirective
import org.junit.jupiter.api.Test

internal class DisabledStateTest : AbstractStateTest() {

    @Test
    internal fun `should be a completion state`() {
        assertThat(DisabledState(campaign).isCompleted).isTrue()
    }

    @Test
    internal fun `should return CompleteCampaignDirective with success on init`() =
        testDispatcherProvider.runTest {
            // when
            every { campaign.message } returns "this is a message"
            val directives = DisabledState(campaign, true).init(factoryService, campaignReportStateKeeper, idGenerator)

            // then
            assertThat(directives).all {
                hasSize(1)
                containsOnly(
                    CompleteCampaignDirective(
                        "my-campaign",
                        true,
                        "this is a message",
                        "the-directive-1",
                        "my-broadcast-channel"
                    )
                )
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return CompleteCampaignDirective with failure on init`() =
        testDispatcherProvider.runTest {
            // when
            every { campaign.message } returns "this is a message"
            val directives = DisabledState(campaign, false).init(factoryService, campaignReportStateKeeper, idGenerator)

            // then
            assertThat(directives).all {
                hasSize(1)
                containsOnly(
                    CompleteCampaignDirective(
                        "my-campaign",
                        false,
                        "this is a message",
                        "the-directive-1",
                        "my-broadcast-channel"
                    )
                )
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }
}