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
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.campaign.FactoryConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
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
        campaign = campaign.copy(
            factories = mutableMapOf(
                "node-1" to FactoryConfiguration(
                    "", mutableMapOf(
                        "scenario-1" to emptyList(),
                        "scenario-2" to emptyList()
                    )
                ),
                "node-2" to FactoryConfiguration(
                    "", mutableMapOf(
                        "scenario-2" to emptyList()
                    )
                )
            )
        )
        operations.saveConfiguration(campaign)
        operations.setState(campaign.id, CampaignRedisState.WARMUP_STATE)
        operations.prepareAssignmentsForFeedbackExpectations(campaign)

        // when
        val directives = RedisFailureState(campaign, "this error", operations).init(
            factoryService,
            campaignReportStateKeeper,
            idGenerator
        )

        // then
        assertThat(campaign.message).isEqualTo("this error")
        assertThat(directives).all {
            hasSize(1)
            containsOnly(
                CampaignShutdownDirective("my-campaign", "the-directive-1", "my-broadcast-channel")
            )
        }
        assertThat(operations.getState(campaign.id)).isNotNull().all {
            prop(Pair<CampaignConfiguration, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<CampaignConfiguration, CampaignRedisState>::second).isEqualTo(CampaignRedisState.FAILURE_STATE)
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
            state.init(factoryService, campaignReportStateKeeper, idGenerator)

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
            assertThat(state.init(factoryService, campaignReportStateKeeper, idGenerator)).isEmpty()
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
            confirmVerified(factoryService, campaignReportStateKeeper)
        }
}