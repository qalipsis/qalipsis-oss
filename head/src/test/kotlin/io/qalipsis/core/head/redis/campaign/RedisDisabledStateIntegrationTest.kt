package io.qalipsis.core.head.redis.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.confirmVerified
import io.mockk.every
import io.qalipsis.core.directives.CompleteCampaignDirective
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisDisabledStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    internal fun `should be a completion state`() {
        assertThat(RedisDisabledState(campaign, true, operations).isCompleted).isTrue()
    }

    @Test
    internal fun `should return CompleteCampaignDirective with success on init`() =
        testDispatcherProvider.run {
            // given
            operations.saveConfiguration(campaign)
            operations.setState(campaign.id, CampaignRedisState.COMPLETION_STATE)
            operations.prepareAssignmentsForFeedbackExpectations(campaign)

            // when
            every { campaign.message } returns "this is a message"
            val directives = RedisDisabledState(campaign, true, operations).init(
                factoryService,
                campaignReportStateKeeper,
                idGenerator
            )

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
            assertThat(redisCommands.keys("*").count()).isEqualTo(0)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return CompleteCampaignDirective with failure on init`() =
        testDispatcherProvider.run {
            // given
            operations.saveConfiguration(campaign)
            operations.setState(campaign.id, CampaignRedisState.FAILURE_STATE)
            operations.prepareAssignmentsForFeedbackExpectations(campaign)
            // when
            every { campaign.message } returns "this is a message"
            val directives = RedisDisabledState(campaign, false, operations).init(
                factoryService,
                campaignReportStateKeeper,
                idGenerator
            )

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
            assertThat(redisCommands.keys("*").count()).isEqualTo(0)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }
}