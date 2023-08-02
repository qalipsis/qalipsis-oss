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
import assertk.assertions.containsExactlyInAnyOrder
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
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import io.qalipsis.core.head.campaign.states.DisabledState
import io.qalipsis.core.head.factory.FactoryHealth
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@WithMockk
@ExperimentalLettuceCoroutinesApi
internal class RedisWarmupStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    override fun tearDownAll() {
        unmockkStatic(Clock::class)
        super.tearDownAll()
    }

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisWarmupState(campaign, operations).isCompleted).isFalse()
    }

    @Test
    fun `should return the directives for profile for each scenario`() = testDispatcherProvider.run {
        // given
        every { campaign.factories } returns mutableMapOf(
            "node-1" to relaxedMockk {
                every { assignment } returns mutableMapOf(
                    "scenario-1" to relaxedMockk(),
                    "scenario-2" to relaxedMockk()
                )
                every { unicastChannel } returns "the-unicast-channel-1"
            },
            "node-2" to relaxedMockk {
                every { assignment } returns mutableMapOf("scenario-2" to relaxedMockk())
                every { unicastChannel } returns "the-unicast-channel-2"
            }
        )
        val state = RedisWarmupState(campaign, operations)

        // when
        val directives = state.run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(directives).all {
            hasSize(3)
            containsOnly(
                ScenarioWarmUpDirective(
                    "my-campaign",
                    "scenario-1",
                    "the-unicast-channel-1"
                ),
                ScenarioWarmUpDirective(
                    "my-campaign",
                    "scenario-2",
                    "the-unicast-channel-1"
                ),
                ScenarioWarmUpDirective(
                    "my-campaign",
                    "scenario-2",
                    "the-unicast-channel-2"
                )
            )
        }
        assertThat(operations.getState(campaign.tenant, campaign.key)).isNotNull().all {
            prop(Pair<RunningCampaign, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<RunningCampaign, CampaignRedisState>::second).isEqualTo(CampaignRedisState.WARMUP_STATE)
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.run {
        // given
        val state = RedisWarmupState(campaign, operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        val newState = state.process(mockk<ScenarioWarmUpFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 1"
        })

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 1")
        }

        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.run {
            // given
            val state = RedisWarmupState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val feedback = mockk<ScenarioWarmUpFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.FAILED
                every { error } returns null
            }

            // when
            val newState = state.process(feedback)

            // then
            assertThat(newState).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("error").isEqualTo("")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return itself in case of any unsupported feedback`() =
        testDispatcherProvider.run {
            // given
            val state = RedisWarmupState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            val newState = state.process(mockk<Feedback>())

            // then
            assertThat(newState).isSameAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should unassign and release factory when feedback is ignored and no more scenarios are assigned to factory`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-1" to relaxedMockk(),
                        "scenario-2" to relaxedMockk()
                    )
                },
                "node-2" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-2" to relaxedMockk()
                    )
                }
            )
            val state = RedisWarmupState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            every { campaign.contains("node-1") } returns false

            // when
            val newState = state.process(mockk<ScenarioWarmUpFeedback> {
                every { nodeId } returns "node-1"
                every { scenarioName } returns "scenario-2"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameAs(state)
            coVerifyOrder {
                campaign.unassignScenarioOfFactory("scenario-2", "node-1")
                campaign.contains("node-1")
                factoryService.releaseFactories(refEq(campaign), listOf("node-1"))
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should unassign but not release factory when feedback is ignored and more scenarios are assigned to factory`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-1" to relaxedMockk(),
                        "scenario-2" to relaxedMockk()
                    )
                },
                "node-2" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-2" to relaxedMockk()
                    )
                }
            )
            val state = RedisWarmupState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            every { campaign.contains("node-1") } returns true

            // when
            val newState = state.process(mockk<ScenarioWarmUpFeedback> {
                every { nodeId } returns "node-1"
                every { scenarioName } returns "scenario-2"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameAs(state)
            coVerifyOrder {
                campaign.unassignScenarioOfFactory("scenario-2", "node-1")
                campaign.contains("node-1")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }


    @Test
    internal fun `should return RedisRunningState when all the declaration feedbacks were received`() =
        testDispatcherProvider.run {
            // given
            val now = getTimeMock()
            every { campaign.startOffsetMs } returns 76521
            every { campaign.broadcastChannel } returns "the-broadcast-channel"
            every { campaign.scenarios } returns mutableMapOf(
                "scenario-1" to relaxedMockk(),
                "scenario-2" to relaxedMockk()
            )
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-1" to relaxedMockk(),
                        "scenario-2" to relaxedMockk()
                    )
                },
                "node-2" to relaxedMockk {
                    every { assignment } returns mutableMapOf(
                        "scenario-2" to relaxedMockk()
                    )
                }
            )
            every { campaign.contains(any()) } returns true
            var state = RedisWarmupState(campaign, operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            var newState = state.process(mockk<ScenarioWarmUpFeedback> {
                every { nodeId } returns "node-1"
                every { scenarioName } returns "scenario-2"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameAs(state)

            // when
            state = RedisWarmupState(campaign, operations)
            state.initialized = true
            assertThat(state.run {
                inject(campaignExecutionContext)
                init()
            }).isEmpty()
            newState = state.process(mockk<ScenarioWarmUpFeedback> {
                every { nodeId } returns "node-2"
                every { scenarioName } returns "scenario-2"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isSameAs(state)

            // when
            state = RedisWarmupState(campaign, operations)
            state.initialized = true
            assertThat(state.run {
                inject(campaignExecutionContext)
                init()
            }).isEmpty()
            newState = state.process(mockk<ScenarioWarmUpFeedback> {
                every { nodeId } returns "node-1"
                every { scenarioName } returns "scenario-1"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(RedisRunningState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
                typedProp<Collection<Directive>>("directivesForInit").all {
                    hasSize(2)
                    containsExactlyInAnyOrder(
                        MinionsStartDirective(
                            "my-campaign",
                            "scenario-1",
                            now.plusMillis(76521),
                            "the-broadcast-channel"
                        ), MinionsStartDirective(
                            "my-campaign",
                            "scenario-2",
                            now.plusMillis(76521),
                            "the-broadcast-channel"
                        )
                    )
                }
            }
            verifyOnce { campaign.unassignScenarioOfFactory(any(), any()) }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    fun `should return a new redis disabled state when no factory can be found`() = testDispatcherProvider.run {
        // given
        val state = RedisWarmupState(campaign, operations)
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
            prop("campaign").isSameAs(campaign)
            prop("isSuccessful").isSameAs(false)
        }
        coVerifyOrder {
            factoryService.getFactoriesHealth(refEq("my-tenant"), mutableSetOf("node-1", "node-2", "node-3"))
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    fun `should return a new redis aborting state when some factories are unhealthy`() = testDispatcherProvider.run {
        // given
        val state = RedisWarmupState(campaign, operations)
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
            prop("campaign").isSameAs(campaign)
            prop("abortConfiguration").isSameAs(abortRunningCampaign)
            prop("error").isSameAs("The campaign was aborted")
            prop("operations").isSameAs(operations)
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
            val state = RedisWarmupState(campaign, operations)
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
                prop("campaign").isSameAs(campaign)
                prop("abortConfiguration").isSameAs(abortRunningCampaign)
                prop("error").isSameAs("The campaign was aborted")
                prop("operations").isSameAs(operations)
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
            val state = RedisWarmupState(campaign, operations)
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
                prop("campaign").isSameAs(campaign)
                prop("isSuccessful").isSameAs(false)
            }
            assertThat(campaign.factories.keys).isEqualTo(mutableSetOf("node-1", "node-2", "node-3"))
            coVerifyOrder {
                factoryService.getFactoriesHealth("my-tenant", mutableSetOf("node-1", "node-2", "node-3"))
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }
}