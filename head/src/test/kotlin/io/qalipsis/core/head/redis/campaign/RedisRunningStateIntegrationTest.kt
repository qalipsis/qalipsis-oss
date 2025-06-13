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
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.feedbacks.CampaignScenarioShutdownFeedback
import io.qalipsis.core.feedbacks.CompleteMinionFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FailedCampaignFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.feedbacks.NodeExecutionFeedback
import io.qalipsis.core.head.campaign.states.DisabledState
import io.qalipsis.core.head.factory.FactoryHealth
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import java.time.Instant

@ExperimentalLettuceCoroutinesApi
internal class RedisRunningStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisRunningState(campaign, operations).isCompleted).isFalse()
    }

    @Test
    fun `should return the directives passed as parameter on init`() = testDispatcherProvider.run {
        // given
        val initDirectives = listOf<Directive>(relaxedMockk(), relaxedMockk())
        val state = RedisRunningState(campaign, operations, false, initDirectives)

        // when
        val directives = state.run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(directives).isSameInstanceAs(initDirectives)

        assertThat(operations.getState(campaign.tenant, campaign.key)).isNotNull().all {
            prop(Pair<RunningCampaign, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<RunningCampaign, CampaignRedisState>::second).isEqualTo(CampaignRedisState.RUNNING_STATE)
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    fun `should return the directives passed as parameter on init but not persist the state`() =
        testDispatcherProvider.run {
            // given
            val initDirectives = listOf<Directive>(relaxedMockk(), relaxedMockk())
            val state = RedisRunningState(campaign, operations, true, initDirectives)
            operations.saveConfiguration(campaign)

            // when
            val directives = state.run {
                inject(campaignExecutionContext)
                init()
            }

            // then
            assertThat(directives).isSameInstanceAs(initDirectives)

            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.run {
        // given
        val state = RedisRunningState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        var newState = state.process(mockk<MinionsDeclarationFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 1"
        })

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameInstanceAs(campaign)
            prop("error").isEqualTo("this is the error 1")
        }

        // when
        newState = state.process(mockk<MinionsRampUpPreparationFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 2"
        })

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameInstanceAs(campaign)
            prop("error").isEqualTo("this is the error 2")
        }

        // when
        newState = state.process(mockk<MinionsStartFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 3"
        })

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameInstanceAs(campaign)
            prop("error").isEqualTo("this is the error 3")
        }

        // when
        newState = state.process(mockk<FailedCampaignFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 4"
        })

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameInstanceAs(campaign)
            prop("error").isEqualTo("this is the error 4")
        }

        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val feedback = mockk<MinionsStartFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.FAILED
                every { error } returns null
            }

            // when
            val newState = state.process(feedback)

            // then
            assertThat(newState).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("error").isEqualTo("")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a failure state when a node cannot be executed`() = testDispatcherProvider.run {
        // given
        campaign.factories.put("node-3", FactoryConfiguration("node-3-channel"))
        val state = RedisRunningState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }
        val feedback = mockk<NodeExecutionFeedback> {
            every { nodeId } returns "node-2"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error"
        }

        // when
        val newState = state.process(feedback)

        // then
        assertThat(campaign.factories).all {
            hasSize(2)
            key("node-1").isNotNull()
            key("node-3").isNotNull()
        }
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameInstanceAs(campaign)
            prop("error").isEqualTo("this is the error")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return itself in case of any unsupported feedback`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<Feedback>())

            // then
            assertThat(newState).isSameInstanceAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisRunningState with a MinionsShutdownDirective when a minion is complete`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<CompleteMinionFeedback> {
                every { scenarioName } returns "the scenario"
                every { minionId } returns "the minion"
            })

            // then
            assertThat(newState).isInstanceOf(RedisRunningState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameInstanceAs(campaign)
                typedProp<Boolean>("doNotPersistStateOnInit").isTrue()
                typedProp<Boolean>("initialized").isFalse()
                typedProp<Collection<Directive>>("directivesForInit").all {
                    hasSize(1)
                    containsOnly(
                        MinionsShutdownDirective(
                            "my-campaign",
                            "the scenario",
                            listOf("the minion"),
                            "my-broadcast-channel"
                        )
                    )
                }
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisRunningState with a CampaignScenarioShutdownDirective when a scenario is complete`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<EndOfCampaignScenarioFeedback> {
                every { campaignKey } returns "my-campaign"
                every { scenarioName } returns "the scenario"
            })

            // then
            assertThat(newState).isInstanceOf(RedisRunningState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameInstanceAs(campaign)
                typedProp<Boolean>("doNotPersistStateOnInit").isTrue()
                typedProp<Boolean>("initialized").isFalse()
                typedProp<Collection<Directive>>("directivesForInit").all {
                    hasSize(1)
                    containsOnly(
                        CampaignScenarioShutdownDirective(
                            "my-campaign",
                            "the scenario",
                            "my-broadcast-channel"
                        )
                    )
                }
            }
            coVerifyOnce { campaignReportStateKeeper.complete("my-campaign", "the scenario") }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisCompletionState when all the scenarios are complete`() =
        testDispatcherProvider.run {
            // given
            every { campaign.scenarios } returns mapOf(
                "scenario-1" to relaxedMockk(),
                "scenario-2" to relaxedMockk()
            )
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            var newState = state.process(mockk<CampaignScenarioShutdownFeedback> {
                every { campaignKey } returns "my-campaign"
                every { scenarioName } returns "scenario-1"
            })

            // then
            assertThat(newState).isSameInstanceAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)

            // when
            newState = state.process(mockk<CampaignScenarioShutdownFeedback> {
                every { campaignKey } returns "my-campaign"
                every { scenarioName } returns "scenario-2"
            })

            // then
            assertThat(newState).isInstanceOf(RedisCompletionState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    fun `should return a new redis disabled state when no factory can be found`() = testDispatcherProvider.run {
        // given
        val state = RedisRunningState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }
        every { campaign.factories.keys } returns mutableSetOf("node-1", "node-2", "node-3")
        coEvery {
            factoryService.getFactoriesHealth(
                "my-tenant",
                mutableSetOf("node-1", "node-2", "node-3")
            )
        } returns setOf()

        // when
        val newState = state.abort(AbortRunningCampaign())

        // then
        assertThat(newState).isInstanceOf(DisabledState::class).all {
            prop("campaign").isSameInstanceAs(campaign)
            prop("isSuccessful").isSameInstanceAs(false)
        }
        coVerifyOrder {
            factoryService.getFactoriesHealth(refEq("my-tenant"), mutableSetOf("node-1", "node-2", "node-3"))
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    fun `should return a new redis aborting state when some factories are unhealthy`() = testDispatcherProvider.run {
        // given
        val state = RedisRunningState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }
        val instant = Instant.now()
        val abortRunningCampaign = AbortRunningCampaign()

        every { campaign.factories.keys } returns mutableSetOf("node-1", "node-2", "node-3")
        coEvery {
            factoryService.getFactoriesHealth(
                "my-tenant",
                mutableSetOf("node-1", "node-2", "node-3")
            )
        } returns setOf(
            FactoryHealth("node-1", instant, Heartbeat.State.IDLE),
            FactoryHealth("node-2", instant, Heartbeat.State.OFFLINE),
            FactoryHealth("node-3", instant, Heartbeat.State.UNHEALTHY)
        )

        // when
        val newState = state.abort(abortRunningCampaign)

        // then
        assertThat(newState).isInstanceOf(RedisAbortingState::class).all {
            prop("campaign").isSameInstanceAs(campaign)
            prop("abortConfiguration").isSameInstanceAs(abortRunningCampaign)
            prop("error").isSameInstanceAs("The campaign was aborted")
            prop("operations").isSameInstanceAs(operations)
        }
        assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1"))
        coVerifyOrder {
            factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1"))
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a new redis aborting state when all factories are healthy`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val instant = Instant.now()
            val abortRunningCampaign = AbortRunningCampaign()

            every { campaign.factories.keys } returns mutableSetOf("node-1", "node-2", "node-3")
            coEvery {
                factoryService.getFactoriesHealth(
                    "my-tenant",
                    mutableSetOf("node-1", "node-2", "node-3")
                )
            } returns setOf(
                FactoryHealth("node-1", instant, Heartbeat.State.IDLE),
                FactoryHealth("node-2", instant, Heartbeat.State.IDLE),
                FactoryHealth("node-3", instant, Heartbeat.State.IDLE)
            )

            // when
            val newState = state.abort(abortRunningCampaign)

            // then
            assertThat(newState).isInstanceOf(RedisAbortingState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("abortConfiguration").isSameInstanceAs(abortRunningCampaign)
                prop("error").isSameInstanceAs("The campaign was aborted")
                prop("operations").isSameInstanceAs(operations)
            }
            assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1", "node-2", "node-3"))
            coVerifyOrder {
                factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1", "node-2", "node-3"))
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new redis aborting state when all factories are unhealthy`() =
        testDispatcherProvider.run {
            // given
            val state = RedisRunningState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val instant = Instant.now()
            val abortRunningCampaign = AbortRunningCampaign()

            every { campaign.factories.keys } returns mutableSetOf("node-1", "node-2", "node-3")
            coEvery {
                factoryService.getFactoriesHealth(
                    "my-tenant",
                    mutableSetOf("node-1", "node-2", "node-3")
                )
            } returns setOf(
                FactoryHealth("node-1", instant, Heartbeat.State.REGISTERED),
                FactoryHealth("node-2", instant, Heartbeat.State.OFFLINE),
                FactoryHealth("node-3", instant, Heartbeat.State.UNHEALTHY)
            )

            // when
            val newState = state.abort(abortRunningCampaign)

            // then
            assertThat(newState).isInstanceOf(DisabledState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("isSuccessful").isSameInstanceAs(false)
            }
            assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1", "node-2", "node-3"))
            coVerifyOrder {
                factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1", "node-2", "node-3"))
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }
}