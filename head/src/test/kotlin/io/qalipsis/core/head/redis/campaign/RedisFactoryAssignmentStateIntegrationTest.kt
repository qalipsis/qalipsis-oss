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
import assertk.assertions.any
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
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.head.model.Factory
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyNever
import org.junit.jupiter.api.Test

@ExperimentalLettuceCoroutinesApi
internal class RedisFactoryAssignmentStateIntegrationTest : AbstractRedisStateIntegrationTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RedisFactoryAssignmentState(campaign, mockk(), mockk(), operations).isCompleted).isFalse()
    }

    @Test
    fun `should return assignment directives on init`() = testDispatcherProvider.run {
        // given
        every { campaign.broadcastChannel } returns "broadcast-channel"
        every { campaign.feedbackChannel } returns "feedback-channel"
        every { campaign.factories } returns mutableMapOf()
        val factories = mockk<Collection<Factory>>()
        val scenarios = mockk<List<ScenarioSummary>>()
        coEvery {
            assignmentResolver.assignFactories(refEq(campaign), refEq(factories), refEq(scenarios))
        } answers {
            // The call to the factory assignment make the campaign return the following factories assignment.
            every { campaign.factories } returns linkedMapOf(
                "node-1" to relaxedMockk {
                    every { unicastChannel } returns "unicast-channel-1"
                    every { assignment } returns linkedMapOf(
                        "scenario-1" to FactoryScenarioAssignment("scenario-1", listOf("dag-1", "dag-2")),
                        "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-A", "dag-B"), 1762)
                    )
                },
                "node-2" to relaxedMockk {
                    every { unicastChannel } returns "unicast-channel-2"
                    every { assignment } returns linkedMapOf(
                        "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-A", "dag-B", "dag-C"), 254)
                    )
                }
            )
        }
        operations.saveConfiguration(campaign)
        val state = RedisFactoryAssignmentState(campaign, factories, scenarios, operations)

        // when
        val directives = state.run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        coVerifyOnce {
            assignmentResolver.assignFactories(refEq(campaign), refEq(factories), refEq(scenarios))
        }
        assertThat(directives).all {
            hasSize(2)
            any {
                it.isInstanceOf(FactoryAssignmentDirective::class).all {
                    prop(FactoryAssignmentDirective::campaignKey).isEqualTo("my-campaign")
                    prop(FactoryAssignmentDirective::assignments).all {
                        hasSize(2)
                        any {
                            it.all {
                                prop(FactoryScenarioAssignment::scenarioName).isEqualTo("scenario-1")
                                prop(FactoryScenarioAssignment::dags).containsOnly("dag-1", "dag-2")
                                prop(FactoryScenarioAssignment::maximalMinionCount).isEqualTo(Int.MAX_VALUE)
                            }
                        }
                        any {
                            it.all {
                                prop(FactoryScenarioAssignment::scenarioName).isEqualTo("scenario-2")
                                prop(FactoryScenarioAssignment::dags).containsOnly("dag-A", "dag-B")
                                prop(FactoryScenarioAssignment::maximalMinionCount).isEqualTo(1762)
                            }
                        }
                    }
                    prop(FactoryAssignmentDirective::runningCampaign).isSameAs(campaign)
                    prop(FactoryAssignmentDirective::channel).isEqualTo("unicast-channel-1")
                }
            }
            any {
                it.isInstanceOf(FactoryAssignmentDirective::class).all {
                    prop(FactoryAssignmentDirective::campaignKey).isEqualTo("my-campaign")
                    prop(FactoryAssignmentDirective::assignments).all {
                        hasSize(1)
                        any {
                            it.all {
                                prop(FactoryScenarioAssignment::scenarioName).isEqualTo("scenario-2")
                                prop(FactoryScenarioAssignment::dags).containsOnly("dag-A", "dag-B", "dag-C")
                                prop(FactoryScenarioAssignment::maximalMinionCount).isEqualTo(254)
                            }
                        }
                    }
                    prop(FactoryAssignmentDirective::runningCampaign).isSameAs(campaign)
                    prop(FactoryAssignmentDirective::channel).isEqualTo("unicast-channel-2")
                }
            }
        }
        assertThat(operations.getState(campaign.tenant, campaign.key)).isNotNull().all {
            prop(Pair<RunningCampaign, CampaignRedisState>::first).isDataClassEqualTo(campaign)
            prop(Pair<RunningCampaign, CampaignRedisState>::second).isEqualTo(CampaignRedisState.FACTORY_DAGS_ASSIGNMENT_STATE)
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.run {
        // given
        val state = RedisFactoryAssignmentState(campaign, mockk(), mockk(), operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }
        val feedback = mockk<FactoryAssignmentFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error"
        }

        // when
        val newState = state.process(feedback)

        // then
        assertThat(newState).isInstanceOf(RedisFailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.run {
            // given
            val state = RedisFactoryAssignmentState(campaign, mockk(), mockk(), operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }
            val feedback = mockk<FactoryAssignmentFeedback> {
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
            val state = RedisFactoryAssignmentState(campaign, mockk(), mockk(), operations)
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
    internal fun `should unassign when feedback is ignored then MinionsAssignmentState when all the feedbacks were received`() =
        testDispatcherProvider.run {
            // given
            every { campaign.factories } returns mutableMapOf(
                "node-1" to relaxedMockk(),
                "node-2" to relaxedMockk()
            )
            var state = RedisFactoryAssignmentState(campaign, mockk(), mockk(), operations)
            state.run {
                inject(campaignExecutionContext)
                init()
            }

            // when
            var newState = state.process(mockk<FactoryAssignmentFeedback> {
                every { nodeId } returns "node-1"
                every { status } returns FeedbackStatus.IGNORED
            })

            // then
            assertThat(newState).isSameAs(state)
            coVerifyOnce {
                campaign.unassignFactory("node-1")
                factoryService.releaseFactories(refEq(campaign), listOf("node-1"))
            }

            // when
            state = RedisFactoryAssignmentState(campaign, mockk(), mockk(), operations)
            state.initialized = true
            assertThat(state.run {
                inject(campaignExecutionContext)
                init()
            }).isEmpty()
            newState = state.process(mockk<FactoryAssignmentFeedback> {
                every { nodeId } returns "node-2"
                every { status } returns FeedbackStatus.COMPLETED
            })

            // then
            assertThat(newState).isInstanceOf(RedisMinionsAssignmentState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            verifyNever { campaign.unassignFactory("node-2") }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RedisAbortingState`() = testDispatcherProvider.run {
        // given
        val state = RedisFactoryAssignmentState(campaign, mockk(), mockk(), operations)
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        val newState = state.abort(AbortRunningCampaign())

        // then
        assertThat(newState).isInstanceOf(RedisAbortingState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isSameAs("The campaign was aborted")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }
}