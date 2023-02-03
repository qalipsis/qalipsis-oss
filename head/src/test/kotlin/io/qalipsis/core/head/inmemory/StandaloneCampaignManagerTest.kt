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

package io.qalipsis.core.head.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.coEvery
import io.mockk.coExcludeRecords
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.FactoryAssignmentState
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.inmemory.catadioptre.currentCampaignState
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
@Timeout(5)
internal class StandaloneCampaignManagerTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var headChannel: HeadChannel

    @RelaxedMockK
    private lateinit var factoryService: FactoryService

    @RelaxedMockK
    private lateinit var campaignService: CampaignService

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @RelaxedMockK
    private lateinit var campaignExecutionContext: CampaignExecutionContext

    @Test
    internal fun `should accept the feedback only if it is a CampaignManagementFeedback`() {
        val campaignManager = standaloneCampaignManager(relaxedMockk())
        assertThat(
            campaignManager.accept(
                relaxedMockk(
                    "campaign-feedback",
                    CampaignManagementFeedback::class
                )
            )
        ).isTrue()
        assertThat(campaignManager.accept(relaxedMockk("non-campaign-feedback"))).isFalse()
    }

    @Test
    internal fun `should start a new campaign when all the scenarios are currently supported`() =
        testDispatcherProvider.run {
            // given
            val campaignManager = standaloneCampaignManager(this)
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(6272),
                    "scenario-2" to ScenarioRequest(12321)
                )
            )
            val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign")
            coEvery {
                campaignService.create("my-tenant", "my-user", refEq(campaign))
            } returns runningCampaign
            val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            val scenario2 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-2" }
            val scenario3 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            coEvery { factoryService.getActiveScenarios(any(), setOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2, scenario3)
            val factory1 =
                relaxedMockk<Factory> { every { nodeId } returns "factory-1"; every { unicastChannel } returns "unicast-channel-1" }
            val factory2 = relaxedMockk<Factory> { every { nodeId } returns "factory-2" }
            val factory3 =
                relaxedMockk<Factory> { every { nodeId } returns "factory-3"; every { unicastChannel } returns "unicast-channel-3" }
            coEvery {
                factoryService.getAvailableFactoriesForScenarios("my-tenant", setOf("scenario-1", "scenario-2"))
            } returns listOf(factory1, factory2, factory3)
            val directive1 = relaxedMockk<FactoryAssignmentDirective>()
            val directive2 = relaxedMockk<FactoryAssignmentDirective>()
            val initialState = mockk<CampaignExecutionState<CampaignExecutionContext>> {
                coEvery { init() } returns listOf(directive1, directive2)
                justRun { inject(any()) }
            }
            coEvery { campaignManager.create(refEq(runningCampaign), any(), any()) } returns initialState

            val countDown = SuspendedCountLatch(2)
            coEvery { headChannel.publishDirective(any()) } coAnswers { countDown.decrement() }

            // when
            val result = campaignManager.start("my-tenant", "my-user", campaign)
            // Wait for the latest directive to be sent.
            countDown.await()

            // then
            assertThat(result).isSameAs(runningCampaign)
            assertThat(campaignManager.currentCampaignState()).isSameAs(initialState)
            coVerifyOrder {
                factoryService.getActiveScenarios(any(), setOf("scenario-1", "scenario-2"))
                campaignService.create("my-tenant", "my-user", refEq(campaign))
                factoryService.getAvailableFactoriesForScenarios("my-tenant", setOf("scenario-1", "scenario-2"))
                campaignService.prepare("my-tenant", "my-campaign")
                headChannel.subscribeFeedback("feedbacks")
                campaignService.start("my-tenant", "my-campaign", any(), isNull())
                campaignService.startScenario("my-tenant", "my-campaign", "scenario-1", any())
                campaignReportStateKeeper.start("my-campaign", "scenario-1")
                campaignService.startScenario("my-tenant", "my-campaign", "scenario-2", any())
                campaignReportStateKeeper.start("my-campaign", "scenario-2")
                campaignManager.create(
                    runningCampaign,
                    listOf(factory1, factory2, factory3),
                    listOf(scenario1, scenario2)
                )
                initialState.inject(campaignExecutionContext)
                initialState.init()
                headChannel.publishDirective(refEq(directive1))
                headChannel.publishDirective(refEq(directive2))
            }
        }

    @Test
    internal fun `should create a factory assignment state as initial state`() = testDispatcherProvider.run {
        // given
        val campaignManager = standaloneCampaignManager(this)
        val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign")
        val scenario1 = mockk<ScenarioSummary>()
        val scenario2 = mockk<ScenarioSummary>()
        val factory1 = mockk<Factory>()
        val factory2 = mockk<Factory>()
        val factory3 = mockk<Factory>()

        // when
        val initialState = campaignManager.create(
            runningCampaign,
            listOf(factory1, factory2, factory3),
            listOf(scenario1, scenario2)
        )

        // then
        assertThat(initialState).isInstanceOf(FactoryAssignmentState::class).all {
            prop("campaign").isSameAs(runningCampaign)
            typedProp<Collection<Factory>>("factories").containsOnly(factory1, factory2, factory3)
            typedProp<Collection<ScenarioSummary>>("scenarios").containsOnly(scenario1, scenario2)
        }
    }

    @Test
    internal fun `should not start a new campaign when some scenarios are currently not supported`() =
        testDispatcherProvider.run {
            // given
            val campaignManager = standaloneCampaignManager(this)
            val campaign = CampaignConfiguration(
                name = "my-campaign",
                scenarios = mapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk()),
            )
            val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            val scenario3 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            coEvery { factoryService.getActiveScenarios(any(), setOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario3)

            // when + then
            assertThrows<IllegalArgumentException> {
                campaignManager.start("my-tenant", "my-user", campaign)
            }
        }

    @Test
    internal fun `should not start a new campaign when one is already running`() =
        testDispatcherProvider.run {
            // given
            val campaignManager = standaloneCampaignManager(this)
            campaignManager.setProperty("currentCampaignState",
                relaxedMockk<CampaignExecutionState<CampaignExecutionContext>> {
                    every { isCompleted } returns false
                })

            // when + then
            assertThrows<IllegalArgumentException> {
                campaignManager.start("my-tenant", "my-user", relaxedMockk())
            }
        }

    @Test
    internal fun `should abort hard a campaign`() = testDispatcherProvider.run {
        //given
        val campaignManager = standaloneCampaignManager(this)
        campaignManager.setProperty(
            "currentCampaignState",
            relaxedMockk<CampaignExecutionState<CampaignExecutionContext>> {
                every { isCompleted } returns false
                coEvery { abort(any()) } returns relaxedMockk<CampaignExecutionState<CampaignExecutionContext>> {
                    every { isCompleted } returns false
                    coEvery { init() } returns listOf(
                        CampaignAbortDirective(
                            "first_campaign",
                            "channel",
                            listOf("scenario-1", "scenario-2"),
                            AbortRunningCampaign(true)
                        )
                    )
                }
            })

        // when
        campaignManager.abort("my-tenant", "my-user", "first_campaign", true)

        // then
        val sentDirectives = mutableListOf<Directive>()
        val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
        coExcludeRecords {
            campaignManager.abort(any(), any(), any(), any())
        }
        coVerifyOrder {
            campaignManager.get("my-tenant", "first_campaign")
            campaignService.abort("my-tenant", "my-user", "first_campaign")
            campaignManager.set(capture(newState))
            campaignReportStateKeeper.abort("first_campaign")
            headChannel.publishDirective(capture(sentDirectives))
        }
        confirmVerified(campaignService, campaignReportStateKeeper, headChannel)

        assertThat(newState.captured).isInstanceOf(CampaignExecutionState::class)
        assertThat(sentDirectives).all {
            hasSize(1)
            any {
                it.isInstanceOf(CampaignAbortDirective::class).all {
                    prop(CampaignAbortDirective::campaignKey).isEqualTo("first_campaign")
                    prop(CampaignAbortDirective::channel).isEqualTo("channel")
                    prop(CampaignAbortDirective::abortRunningCampaign).all {
                        typedProp<Boolean>("hard").isEqualTo(true)
                    }
                    prop(CampaignAbortDirective::scenarioNames).all {
                        hasSize(2)
                        index(0).isEqualTo("scenario-1")
                        index(1).isEqualTo("scenario-2")
                    }
                }
            }
        }
    }

    @Test
    internal fun `should abort soft a campaign`() = testDispatcherProvider.run {
        //given
        val campaignManager = standaloneCampaignManager(this)
        campaignManager.setProperty(
            "currentCampaignState",
            relaxedMockk<CampaignExecutionState<CampaignExecutionContext>> {
                every { isCompleted } returns false
                coEvery { abort(any()) } returns relaxedMockk<CampaignExecutionState<CampaignExecutionContext>> {
                    every { isCompleted } returns false
                    coEvery { init() } returns listOf(
                        CampaignAbortDirective(
                            "first_campaign",
                            "channel",
                            listOf("scenario-1", "scenario-2"),
                            AbortRunningCampaign(false)
                        )
                    )
                }
            })

        // when
        campaignManager.abort("my-tenant", "my-user", "first_campaign", false)

        // then
        val sentDirectives = mutableListOf<Directive>()
        val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
        coExcludeRecords {
            campaignManager.abort(any(), any(), any(), any())
        }
        coVerifyOrder {
            campaignManager.get("my-tenant", "first_campaign")
            campaignService.abort("my-tenant", "my-user", "first_campaign")
            campaignManager.set(capture(newState))
            headChannel.publishDirective(capture(sentDirectives))
        }
        confirmVerified(campaignService, campaignReportStateKeeper, headChannel)

        assertThat(newState.captured).isInstanceOf(CampaignExecutionState::class)
        assertThat(sentDirectives).all {
            hasSize(1)
            any {
                it.isInstanceOf(CampaignAbortDirective::class).all {
                    prop(CampaignAbortDirective::campaignKey).isEqualTo("first_campaign")
                    prop(CampaignAbortDirective::channel).isEqualTo("channel")
                    prop(CampaignAbortDirective::abortRunningCampaign).all {
                        typedProp<Boolean>("hard").isEqualTo(false)
                    }
                    prop(CampaignAbortDirective::scenarioNames).all {
                        hasSize(2)
                        index(0).isEqualTo("scenario-1")
                        index(1).isEqualTo("scenario-2")
                    }
                }
            }
        }
    }

    @Test
    internal fun `should replay a campaign `() = testDispatcherProvider.run {
        // given
        val campaignManager = standaloneCampaignManager(this)
        val campaignConfiguration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(6272),
                "scenario-2" to ScenarioRequest(12321)
            )
        )
        val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign")
        coEvery { campaignService.retrieveConfiguration("my-tenant", "my-campaign") } returns campaignConfiguration
        coEvery { campaignManager.start("my-tenant", "my-user", campaignConfiguration) } returns runningCampaign

        // when
        val result = campaignManager.replay("my-tenant", "my-user", "my-campaign")

        // then
        assertThat(result).isSameAs(runningCampaign)
        coExcludeRecords { campaignManager.replay("my-tenant", "my-user", "my-campaign") }
        coVerifyOrder {
            campaignService.retrieveConfiguration("my-tenant", "my-campaign")
            campaignManager.start("my-tenant", "my-user", campaignConfiguration)
        }
        confirmVerified(campaignManager, campaignService)
    }

    private fun standaloneCampaignManager(scope: CoroutineScope) =
        spyk(
            StandaloneCampaignManager(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                headConfiguration,
                scope,
                campaignExecutionContext
            ), recordPrivateCalls = true
        )
}