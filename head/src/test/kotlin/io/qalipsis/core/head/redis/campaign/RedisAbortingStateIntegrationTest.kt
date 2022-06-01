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
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.configuration.AbortCampaignConfiguration
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisAbortingStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisAbortingState(campaign, AbortCampaignConfiguration(), "", operations).isCompleted).isFalse()
    }

    @Test
    internal fun `should return campaign abort directive on init`() = testDispatcherProvider.run {
        // given
        every { campaign.scenarios } returns mapOf(
            "scenario-1" to relaxedMockk { every { minionsCount } returns 54 },
            "scenario-2" to relaxedMockk { every { minionsCount } returns 43 }
        )
        operations.saveConfiguration(campaign)
        operations.setState(campaign.tenant, campaign.key, CampaignRedisState.ABORTING_STATE)
        operations.prepareAssignmentsForFeedbackExpectations(campaign)

        // when
        val directives = RedisAbortingState(campaign, AbortCampaignConfiguration(), "Aborting campaign", operations).run {
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
            prop(Pair<CampaignConfiguration, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<CampaignConfiguration, CampaignRedisState>::second).isEqualTo(CampaignRedisState.ABORTING_STATE)
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
                AbortCampaignConfiguration(hard = true),
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
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisCompletionState when a CampaignAbortFeedback and all the feedbacks were received and the abort is soft`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf("node-1" to relaxedMockk())

            val state = RedisAbortingState(
                campaign,
                AbortCampaignConfiguration(hard = false),
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
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisAbortingState`() = testDispatcherProvider.run {
        // given
        val state = RedisAbortingState(
            campaign,
            AbortCampaignConfiguration(true),
            "The campaign was aborted",
            operations
        )
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        val newState = state.abort(AbortCampaignConfiguration())

        // then
        assertThat(newState).isInstanceOf(RedisAbortingState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isSameAs("The campaign was aborted")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }
}