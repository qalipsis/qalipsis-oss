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
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
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
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class RunningStateTest : AbstractStateTest() {

    @Test
    fun `should not be a completion state`() {
        assertThat(RunningState(campaign).isCompleted).isFalse()
    }

    @Test
    fun `should return the directives passed as parameter on init`() = testDispatcherProvider.runTest {
        // given
        val initDirectives = listOf<Directive>(relaxedMockk(), relaxedMockk())
        val state = RunningState(campaign, initDirectives)

        // when
        val directives = state.run {
            inject(campaignExecutionContext)
            init()
        }

        // then
        assertThat(directives).isSameAs(initDirectives)
        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure`() = testDispatcherProvider.runTest {
        // given
        val state = RunningState(campaign)
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        var newState = state.process(mockk<MinionsRampUpPreparationFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 1"
        })

        // then
        assertThat(newState).isInstanceOf(FailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 1")
        }

        // when
        newState = state.process(mockk<MinionsStartFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 2"
        })

        // then
        assertThat(newState).isInstanceOf(FailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 2")
        }

        // when
        newState = state.process(mockk<FailedCampaignFeedback> {
            every { nodeId } returns "node-1"
            every { status } returns FeedbackStatus.FAILED
            every { error } returns "this is the error 3"
        })

        // then
        assertThat(newState).isInstanceOf(FailureState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isEqualTo("this is the error 3")
        }

        confirmVerified(factoryService, campaignReportStateKeeper)
    }

    @Test
    internal fun `should return a failure state when the feedback is failure without error message`() =
        testDispatcherProvider.runTest {
            // given
            val state = RunningState(campaign)
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
            assertThat(newState).isInstanceOf(FailureState::class).all {
                prop("campaign").isSameAs(campaign)
                prop("error").isEqualTo("")
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return itself in case of any unsupported feedback`() =
        testDispatcherProvider.runTest {
            // given
            val state = RunningState(campaign)
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
    internal fun `should return a new RunningState with a MinionsShutdownDirective when a minion is complete`() =
        testDispatcherProvider.runTest {
            // given
            val state = RunningState(campaign)
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
            assertThat(newState).isInstanceOf(RunningState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameAs(campaign)
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
                prop("expectedScenariosToComplete").isSameAs(state.getProperty("expectedScenariosToComplete"))
            }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new RunningState with a CampaignScenarioShutdownDirective when a scenario is complete`() =
        testDispatcherProvider.runTest {
            // given
            val state = RunningState(campaign)
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
            assertThat(newState).isInstanceOf(RunningState::class).all {
                isNotSameAs(state)
                prop("campaign").isSameAs(campaign)
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
                prop("expectedScenariosToComplete").isSameAs(state.getProperty("expectedScenariosToComplete"))
            }
            coVerifyOnce { campaignReportStateKeeper.complete("my-campaign", "the scenario") }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    internal fun `should return a new CompletionState when all the scenarios are complete`() =
        testDispatcherProvider.runTest {
            // given
            every { campaign.scenarios } returns mapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
            val state = RunningState(campaign)
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
            assertThat(newState).isSameAs(state)
            confirmVerified(factoryService, campaignReportStateKeeper)

            // when
            newState = state.process(mockk<CampaignScenarioShutdownFeedback> {
                every { campaignKey } returns "my-campaign"
                every { scenarioName } returns "scenario-2"
            })

            // then
            assertThat(newState).isInstanceOf(CompletionState::class).all {
                prop("campaign").isSameAs(campaign)
                typedProp<Boolean>("initialized").isFalse()
            }
            coVerifyOnce { campaignReportStateKeeper.complete("my-campaign") }
            confirmVerified(factoryService, campaignReportStateKeeper)
        }

    @Test
    fun `should return an AbortingState`() = testDispatcherProvider.runTest {
        // given
        val state = RunningState(campaign)
        state.run {
            inject(campaignExecutionContext)
            init()
        }

        // when
        val newState = state.abort(AbortRunningCampaign())

        // then
        assertThat(newState).isInstanceOf(AbortingState::class).all {
            prop("campaign").isSameAs(campaign)
            prop("error").isSameAs("The campaign was aborted")
        }
        confirmVerified(factoryService, campaignReportStateKeeper)
    }
}