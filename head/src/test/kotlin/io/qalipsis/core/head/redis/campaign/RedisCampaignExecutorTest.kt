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
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.coEvery
import io.mockk.coExcludeRecords
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.CampaignTimeoutFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.head.campaign.CampaignConstraintsProvider
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.ChannelNameFactory
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.DefaultCampaignConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.lock.LockProvider
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@ExperimentalLettuceCoroutinesApi
@WithMockk
@Timeout(10)
internal class RedisCampaignExecutorTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var headChannel: HeadChannel

    @MockK
    lateinit var factoryService: FactoryService

    @RelaxedMockK
    lateinit var campaignService: CampaignService

    @RelaxedMockK
    lateinit var operations: CampaignRedisOperations

    @RelaxedMockK
    lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @MockK
    private lateinit var campaignExecutionContext: CampaignExecutionContext

    @RelaxedMockK
    lateinit var campaignConstraintsProvider: CampaignConstraintsProvider

    @MockK
    lateinit var defaultCampaignConfiguration: DefaultCampaignConfiguration

    @RelaxedMockK
    lateinit var validation: DefaultCampaignConfiguration.Validation

    @RelaxedMockK
    private lateinit var campaignHook1: CampaignHook

    @RelaxedMockK
    private lateinit var campaignHook2: CampaignHook

    @RelaxedMockK
    private lateinit var lockProvider: LockProvider

    @RelaxedMockK
    private lateinit var channelNameFactory: ChannelNameFactory

    @BeforeEach
    fun setUp() {
        coEvery { lockProvider.withLock(any(), any()) } coAnswers { secondArg<suspend () -> Unit>().invoke() }
    }

    @Test
    internal fun `should accept the feedback only if it is a CampaignManagementFeedback`() {
        val campaignExecutor = redisCampaignExecutor(relaxedMockk())
        assertThat(
            campaignExecutor.accept(
                relaxedMockk(
                    "campaign-feedback",
                    CampaignManagementFeedback::class
                )
            )
        ).isTrue()
        assertThat(campaignExecutor.accept(relaxedMockk("non-campaign-feedback"))).isFalse()

        confirmVerified(
            headChannel,
            factoryService,
            campaignService,
            campaignReportStateKeeper,
            campaignConstraintsProvider,
            campaignExecutionContext,
            operations
        )
    }

    @Test
    internal fun `should start a new campaign when all the scenarios are currently supported and release the unused factories`() =
        testDispatcherProvider.run {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(6272),
                    "scenario-2" to ScenarioRequest(12321)
                ),
                timeout = Duration.ofMinutes(1),
                hardTimeout = false
            )
            val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign")
            coEvery {
                campaignService.create("my-tenant", "my-user", refEq(campaign))
            } returns runningCampaign
            validation = mockk {
                every { maxExecutionDuration } returns Duration.ofMinutes(7)
                every { maxMinionsCount } returns 9000
            }
            every { defaultCampaignConfiguration.validation } returns validation
            coEvery { campaignConstraintsProvider.supply(any()) } returns defaultCampaignConfiguration
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
            coEvery { campaignExecutor.createInitialState(refEq(runningCampaign), any(), any()) } returns initialState

            val countDown = SuspendedCountLatch(2)
            coEvery { headChannel.publishDirective(any()) } coAnswers { countDown.decrement() }
            coEvery { channelNameFactory.getFeedbackChannelName(campaign = any()) } returns "feedbacks"

            // when
            val result = campaignExecutor.start("my-tenant", "my-user", campaign)
            // Wait for the latest directive to be sent.
            countDown.await()

            // then
            assertThat(result).isSameInstanceAs(runningCampaign)
            coVerifyOrder {
                factoryService.getActiveScenarios("my-tenant", setOf("scenario-1", "scenario-2"))
                campaignService.create("my-tenant", "my-user", refEq(campaign))
                factoryService.getAvailableFactoriesForScenarios("my-tenant", setOf("scenario-1", "scenario-2"))
                campaignService.prepare("my-tenant", "my-campaign")
                headChannel.subscribeFeedback("feedbacks")
                campaignConstraintsProvider.supply(any())
                campaignHook1.preStart(refEq(runningCampaign))
                campaignHook2.preStart(refEq(runningCampaign))
                campaignService.start("my-tenant", "my-campaign", any(), any(), any())
                campaignService.startScenario("my-tenant", "my-campaign", "scenario-1", any())
                campaignReportStateKeeper.start("my-campaign", "scenario-1")
                campaignService.startScenario("my-tenant", "my-campaign", "scenario-2", any())
                campaignReportStateKeeper.start("my-campaign", "scenario-2")
                campaignExecutor.createInitialState(
                    runningCampaign,
                    listOf(factory1, factory2, factory3),
                    listOf(scenario1, scenario2)
                )
                initialState.inject(campaignExecutionContext)
                initialState.init()
                headChannel.publishDirective(refEq(directive1))
                headChannel.publishDirective(refEq(directive2))
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations,
                campaignHook1,
                campaignHook2
            )

        }

    @Test
    internal fun `should create a redis state as initial state`() = testDispatcherProvider.run {
        // given
        val campaignExecutor = redisCampaignExecutor(this)
        val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign")
        val scenario1 = mockk<ScenarioSummary>()
        val scenario2 = mockk<ScenarioSummary>()
        val factory1 = mockk<Factory>()
        val factory2 = mockk<Factory>()
        val factory3 = mockk<Factory>()

        // when
        val initialState = campaignExecutor.createInitialState(
            runningCampaign,
            listOf(factory1, factory2, factory3),
            listOf(scenario1, scenario2)
        )

        // then
        assertThat(initialState).isInstanceOf(RedisFactoryAssignmentState::class).all {
            prop("campaign").isSameInstanceAs(runningCampaign)
            typedProp<Collection<Factory>>("factories").containsOnly(factory1, factory2, factory3)
            typedProp<Collection<ScenarioSummary>>("scenarios").containsOnly(scenario1, scenario2)
        }

        confirmVerified(
            headChannel,
            factoryService,
            campaignService,
            campaignReportStateKeeper,
            campaignConstraintsProvider,
            campaignExecutionContext,
            operations
        )
    }

    @Test
    internal fun `should close the campaign after creation when an exception occurs`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf("scenario-1" to ScenarioRequest(6272))
            )
            val runningCampaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign")
            coEvery {
                campaignService.create("my-tenant", "my-user", refEq(campaign))
            } returns runningCampaign
            coEvery { factoryService.getActiveScenarios("my-tenant", setOf("scenario-1")) } returns
                    listOf(relaxedMockk { every { name } returns "scenario-1" })
            coEvery {
                factoryService.getAvailableFactoriesForScenarios(
                    "my-tenant",
                    any()
                )
            } throws RuntimeException("Something wrong occurred")
            val latch = Latch(true)
            coEvery { campaignService.close(any(), any(), any(), any()) } coAnswers { latch.release(); relaxedMockk() }

            // when
            val exception = assertThrows<RuntimeException> {
                campaignExecutor.start("my-tenant", "my-user", campaign)
            }
            latch.await()

            // then
            assertThat(exception.message).isEqualTo("Something wrong occurred")
            coVerifyOrder {
                factoryService.getActiveScenarios("my-tenant", setOf("scenario-1"))
                campaignService.create("my-tenant", "my-user", refEq(campaign))
                factoryService.getAvailableFactoriesForScenarios("my-tenant", setOf("scenario-1"))
                campaignReportStateKeeper.complete("my-campaign", ExecutionStatus.FAILED, "Something wrong occurred")
                campaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED, "Something wrong occurred")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should not start a new campaign when some scenarios are currently not supported`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = CampaignConfiguration(
                name = "my-campaign",
                scenarios = mapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk()),
            )
            val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            coEvery { factoryService.getActiveScenarios(any(), setOf("scenario-1", "scenario-2")) } returns listOf(
                scenario1
            )

            // when + then
            assertThrows<IllegalArgumentException> {
                campaignExecutor.start("my-tenant", "my-user", campaign)
            }
            coVerifyOrder {
                factoryService.getActiveScenarios(any(), setOf("scenario-1", "scenario-2"))
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should return RedisFactoryAssignmentState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.FACTORY_DAGS_ASSIGNMENT_STATE)

            // when
            val state = campaignExecutor.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisFactoryAssignmentState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("operations").isSameInstanceAs(operations)
                prop("context").isSameInstanceAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
            coVerifyOrder {
                operations.getState("my-tenant", "my-campaign")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should return RedisMinionsAssignmentState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.MINIONS_ASSIGNMENT_STATE)

            // when
            val state = campaignExecutor.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisMinionsAssignmentState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("operations").isSameInstanceAs(operations)
                prop("context").isSameInstanceAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
            coVerifyOrder {
                operations.getState("my-tenant", "my-campaign")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should return RedisWarmupState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.WARMUP_STATE)

            // when
            val state = campaignExecutor.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisWarmupState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("operations").isSameInstanceAs(operations)
                prop("context").isSameInstanceAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
            coVerifyOrder {
                operations.getState("my-tenant", "my-campaign")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should return RedisMinionsStartupState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.MINIONS_STARTUP_STATE)

            // when
            val state = campaignExecutor.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisMinionsScheduleRampUpState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("operations").isSameInstanceAs(operations)
                prop("context").isSameInstanceAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
            coVerifyOrder {
                operations.getState("my-tenant", "my-campaign")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should return RedisRunningState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.RUNNING_STATE)

            // when
            val state = campaignExecutor.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisRunningState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("operations").isSameInstanceAs(operations)
                prop("context").isSameInstanceAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
            coVerifyOrder {
                operations.getState("my-tenant", "my-campaign")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should return RedisCompletionState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.COMPLETION_STATE)

            // when
            val state = campaignExecutor.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisCompletionState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("operations").isSameInstanceAs(operations)
                prop("context").isSameInstanceAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
            coVerifyOrder {
                operations.getState("my-tenant", "my-campaign")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should return RedisFailureState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.FAILURE_STATE)

            // when
            val state = campaignExecutor.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisFailureState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("operations").isSameInstanceAs(operations)
                prop("context").isSameInstanceAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
            coVerifyOrder {
                operations.getState("my-tenant", "my-campaign")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should return RedisAbortingState`() =
        testDispatcherProvider.runTest {
            // given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = relaxedMockk<RunningCampaign>()
            coEvery {
                operations.getState(
                    "my-tenant",
                    "my-campaign"
                )
            } returns (campaign to CampaignRedisState.ABORTING_STATE)

            // when
            val state = campaignExecutor.get("my-tenant", "my-campaign")

            // then
            assertThat(state).isInstanceOf(RedisAbortingState::class).all {
                prop("campaign").isSameInstanceAs(campaign)
                prop("operations").isSameInstanceAs(operations)
                prop("context").isSameInstanceAs(campaignExecutionContext)
                typedProp<Boolean>("initialized").isTrue()
            }
            coVerifyOrder {
                operations.getState("my-tenant", "my-campaign")
            }

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should abort hard a campaign`() = testDispatcherProvider.run {
        //given
        val campaignExecutor = redisCampaignExecutor(this)
        val campaign = RunningCampaign(
            tenant = "my-tenant",
            key = "first_campaign",
            scenarios = linkedMapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
        )
        coEvery {
            operations.getState("my-tenant", "first_campaign")
        } returns Pair(campaign, CampaignRedisState.RUNNING_STATE)
        val initialCampaignState = mockk<RedisRunningState> {
            every { campaignKey } returns "first_campaign"
        }
        coJustRun { campaignService.abort("my-tenant", "my-user", "first_campaign") }
        val abortedCampaignState = mockk<RedisRunningState> {
            every { campaignKey } returns "first_campaign"
            coEvery { init() } returns listOf(
                CampaignAbortDirective(
                    "first_campaign",
                    "channel",
                    listOf("scenario-1", "scenario-2")
                )
            )
            every { isCompleted } returns false
        }
        coEvery { initialCampaignState.abort(any()) } returns abortedCampaignState
        justRun { abortedCampaignState.inject(campaignExecutionContext) }
        coEvery { campaignExecutor.get("my-tenant", "first_campaign") } returns initialCampaignState
        campaign.message = "problem"
        campaign.broadcastChannel = "channel"

        // when
        campaignExecutor.abort("my-tenant", "my-user", "first_campaign", true)

        // then
        val sentDirectives = mutableListOf<Directive>()
        val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
        coVerifyOrder {
            campaignExecutor.get("my-tenant", "first_campaign")
            campaignService.abort("my-tenant", "my-user", "first_campaign")
            campaignReportStateKeeper.abort("first_campaign")
            campaignExecutor.set(capture(newState))
            headChannel.publishDirective(capture(sentDirectives))
        }
        assertThat(newState.captured).isInstanceOf(RedisRunningState::class).all {
            prop("isCompleted").isEqualTo(false)
            prop("campaignKey").isEqualTo("first_campaign")
        }
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

        confirmVerified(
            headChannel,
            factoryService,
            campaignService,
            campaignReportStateKeeper,
            campaignConstraintsProvider,
            campaignExecutionContext,
            operations
        )
    }

    @Test
    internal fun `should abort soft a campaign`() = testDispatcherProvider.run {
        //given
        val campaignExecutor = redisCampaignExecutor(this)
        val campaign = RunningCampaign(
            tenant = "my-tenant",
            key = "first_campaign",
            scenarios = linkedMapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
        )
        coEvery {
            operations.getState("my-tenant", "first_campaign")
        } returns Pair(campaign, CampaignRedisState.RUNNING_STATE)
        val initialCampaignState = mockk<RedisRunningState> {
            every { campaignKey } returns "first_campaign"
            every { initialized } returns true
        }
        coJustRun { campaignService.abort("my-tenant", "my-user", "first_campaign") }
        val abortedCampaignState = mockk<RedisRunningState> {
            every { campaignKey } returns "first_campaign"
            coEvery { init() } returns listOf(
                CampaignAbortDirective(
                    "first_campaign",
                    "channel",
                    listOf("scenario-1", "scenario-2"),
                    AbortRunningCampaign(false)
                )
            )
            every { isCompleted } returns false
        }
        coEvery { initialCampaignState.abort(any()) } returns abortedCampaignState
        justRun { abortedCampaignState.inject(campaignExecutionContext) }
        coEvery { campaignExecutor.get("my-tenant", "first_campaign") } returns initialCampaignState
        campaign.message = "problem"
        campaign.broadcastChannel = "channel"

        // when
        campaignExecutor.abort("my-tenant", "my-user", "first_campaign", false)

        // then
        val sentDirectives = mutableListOf<Directive>()
        val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
        coVerifyOrder {
            campaignExecutor.get("my-tenant", "first_campaign")
            campaignService.abort("my-tenant", "my-user", "first_campaign")
            campaignExecutor.set(capture(newState))
            headChannel.publishDirective(capture(sentDirectives))
        }
        assertThat(newState.captured).isInstanceOf(RedisRunningState::class).all {
            prop("isCompleted").isEqualTo(false)
            prop("campaignKey").isEqualTo("first_campaign")
        }
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

        confirmVerified(
            headChannel,
            factoryService,
            campaignService,
            campaignReportStateKeeper,
            campaignConstraintsProvider,
            campaignExecutionContext,
            operations
        )
    }

    @Test
    internal fun `should replay a campaign`() = testDispatcherProvider.runTest {
        // given
        val campaignExecutor = redisCampaignExecutor(this)
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
        coEvery { campaignExecutor.start("my-tenant", "my-user", campaignConfiguration) } returns runningCampaign

        // when
        val result = campaignExecutor.replay("my-tenant", "my-user", "my-campaign")

        // then
        assertThat(result).isSameInstanceAs(runningCampaign)
        coExcludeRecords { campaignExecutor.replay("my-tenant", "my-user", "my-campaign") }
        coVerifyOrder {
            campaignService.retrieveConfiguration("my-tenant", "my-campaign")
            campaignExecutor.start("my-tenant", "my-user", campaignConfiguration)
        }

        confirmVerified(
            campaignExecutor,
            headChannel,
            factoryService,
            campaignService,
            campaignReportStateKeeper,
            campaignConstraintsProvider,
            campaignExecutionContext,
            operations
        )
    }

    @Test
    internal fun `should abort the campaign softly when a CampaignTimeoutFeedback with hard equals false is received`() =
        testDispatcherProvider.runTest {
            //given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = RunningCampaign(
                tenant = "my-tenant",
                key = "first_campaign",
                scenarios = linkedMapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk())
            ).apply {
                message = "problem"
                broadcastChannel = "channel"
            }
            campaign.message = "problem"
            campaign.broadcastChannel = "channel"
            coEvery {
                operations.getState("my-tenant", "first_campaign")
            } returns Pair(campaign, CampaignRedisState.RUNNING_STATE)
            val initialCampaignState = mockk<RedisRunningState> {
                every { campaignKey } returns "first_campaign"
            }
            coEvery { campaignExecutor.get("my-tenant", "first_campaign") } returns initialCampaignState
            val abortedCampaignState = mockk<RedisRunningState> {
                every { campaignKey } returns "first_campaign"
                coEvery { init() } returns listOf(
                    CampaignAbortDirective(
                        "first_campaign",
                        "channel",
                        listOf("scenario-1", "scenario-2"),
                        AbortRunningCampaign(false)
                    )
                )
                every { isCompleted } returns false
            }
            coEvery { initialCampaignState.abort(any()) } returns abortedCampaignState
            justRun { abortedCampaignState.inject(campaignExecutionContext) }
            val timeoutFeedback = CampaignTimeoutFeedback(
                campaignKey = "first_campaign",
                hard = false,
                status = FeedbackStatus.FAILED,
                errorMessage = "Running campaign timed out",
            ).apply {
                tenant = "my-tenant"
            }

            // when
            campaignExecutor.notify(timeoutFeedback)

            // then
            val sentDirectives = mutableListOf<Directive>()
            val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
            coVerifyOrder {
                campaignExecutor.notify(timeoutFeedback)
                campaignExecutor.get("my-tenant", "first_campaign")
                campaignExecutor.get("my-tenant", "first_campaign")
                campaignService.abort("my-tenant", null, "first_campaign")
                campaignExecutor.set(capture(newState))
                headChannel.publishDirective(capture(sentDirectives))
            }
            assertThat(newState.captured).isInstanceOf(RedisRunningState::class).all {
                prop("isCompleted").isEqualTo(false)
                prop("campaignKey").isEqualTo("first_campaign")
            }
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

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext,
                operations
            )
        }

    @Test
    internal fun `should abort the campaign hardly when a CampaignTimeoutFeedback with hard equals true is received`() =
        testDispatcherProvider.run {
            //given
            val campaignExecutor = redisCampaignExecutor(this)
            val campaign = RunningCampaign(
                tenant = "my-tenant",
                key = "first_campaign",
                scenarios = linkedMapOf("scenario-1" to relaxedMockk(), "scenario-2" to relaxedMockk()),
            ).apply {
                message = "problem"
                broadcastChannel = "channel"
            }
            coEvery {
                operations.getState("my-tenant", "first_campaign")
            } returns Pair(campaign, CampaignRedisState.RUNNING_STATE)
            val initialCampaignState = mockk<RedisRunningState> {
                every { campaignKey } returns "first_campaign"
            }
            coEvery { campaignExecutor.get("my-tenant", "first_campaign") } returns initialCampaignState
            val abortedCampaignState = mockk<RedisRunningState> {
                every { campaignKey } returns "first_campaign"
                coEvery { init() } returns listOf(
                    CampaignAbortDirective(
                        "first_campaign",
                        "channel",
                        listOf("scenario-1", "scenario-2")
                    )
                )
                every { isCompleted } returns false
            }
            coEvery { initialCampaignState.abort(any()) } returns abortedCampaignState
            justRun { abortedCampaignState.inject(campaignExecutionContext) }
            val timeoutFeedback = CampaignTimeoutFeedback(
                campaignKey = "first_campaign",
                hard = true,
                status = FeedbackStatus.FAILED,
                errorMessage = "Running campaign timed out",
            ).apply {
                tenant = "my-tenant"
            }

            // when
            campaignExecutor.notify(timeoutFeedback)

            // then
            val sentDirectives = mutableListOf<Directive>()
            val newState = slot<CampaignExecutionState<CampaignExecutionContext>>()
            coVerifyOrder {
                campaignExecutor.notify(timeoutFeedback)
                campaignExecutor.get("my-tenant", "first_campaign")
                campaignExecutor.get("my-tenant", "first_campaign")
                campaignService.abort("my-tenant", null, "first_campaign")
                campaignExecutor.set(capture(newState))
                campaignReportStateKeeper.abort("first_campaign")
                headChannel.publishDirective(capture(sentDirectives))
            }
            assertThat(newState.captured).isInstanceOf(RedisRunningState::class).all {
                prop("isCompleted").isEqualTo(false)
                prop("campaignKey").isEqualTo("first_campaign")
            }
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

            confirmVerified(
                headChannel,
                factoryService,
                campaignService,
                campaignReportStateKeeper,
                campaignConstraintsProvider,
                campaignExecutionContext
            )
        }

    private fun redisCampaignExecutor(scope: CoroutineScope) =
        spyk(
            RedisCampaignExecutor(
                headChannel = headChannel,
                factoryService = factoryService,
                campaignService = campaignService,
                campaignReportStateKeeper = campaignReportStateKeeper,
                campaignConstraintsProvider = campaignConstraintsProvider,
                campaignHooks = listOf(campaignHook1, campaignHook2),
                coroutineScope = scope,
                campaignExecutionContext = campaignExecutionContext,
                redisOperations = operations,
                lockProvider = lockProvider,
                channelNameFactory = channelNameFactory
            ), recordPrivateCalls = true
        )
}