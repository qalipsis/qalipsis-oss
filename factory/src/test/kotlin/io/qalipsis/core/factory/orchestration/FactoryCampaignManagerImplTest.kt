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

package io.qalipsis.core.factory.orchestration

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isEqualToIgnoringGivenProperties
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.matches
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.executionprofile.AcceleratingExecutionProfile
import io.qalipsis.api.executionprofile.CompletionMode.GRACEFUL
import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.executionprofile.ExecutionProfileIterator
import io.qalipsis.api.executionprofile.ImmediateExecutionProfile
import io.qalipsis.api.executionprofile.MinionsStartingLine
import io.qalipsis.api.executionprofile.PercentageStageExecutionProfile
import io.qalipsis.api.executionprofile.ProgressiveVolumeExecutionProfile
import io.qalipsis.api.executionprofile.RegularExecutionProfile
import io.qalipsis.api.executionprofile.StageExecutionProfile
import io.qalipsis.api.executionprofile.TimeFrameExecutionProfile
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.runtime.ScenarioStartStopConfiguration
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ImmediateExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.PercentageStage
import io.qalipsis.core.executionprofile.PercentageStageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ProgressiveVolumeExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.TimeFrameExecutionProfileConfiguration
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.catadioptre.assignableScenariosExecutionProfiles
import io.qalipsis.core.factory.orchestration.catadioptre.convertExecutionProfile
import io.qalipsis.core.factory.orchestration.catadioptre.runningCampaign
import io.qalipsis.core.factory.steps.ContextConsumer
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import java.util.Optional

@WithMockk
internal class FactoryCampaignManagerImplTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var defaultExecutionProfile: ExecutionProfile

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var sharedStateRegistry: SharedStateRegistry

    @RelaxedMockK
    private lateinit var contextConsumer: ContextConsumer

    @RelaxedMockK
    private lateinit var campaign: Campaign

    @RelaxedMockK
    private lateinit var contextConsumerOptional: Optional<ContextConsumer>

    @RelaxedMockK
    lateinit var campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry

    @BeforeEach
    internal fun setUp() {
        every { contextConsumerOptional.get() } returns contextConsumer
    }

    @Test
    internal fun `should init the campaign for the factory and return whether the scenario is run locally`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = spyk(buildCampaignManager(), recordPrivateCalls = true)
            every { scenarioRegistry.contains("scenario-1") } returns true
            every { scenarioRegistry.contains("scenario-2") } returns true
            val defaultScenarioExecutionProfile1 = mockk<ExecutionProfile>()
            every { scenarioRegistry.get("scenario-1")!!.executionProfile } returns defaultScenarioExecutionProfile1
            val defaultScenarioExecutionProfile2 = mockk<ExecutionProfile>()
            every { scenarioRegistry.get("scenario-2")!!.executionProfile } returns defaultScenarioExecutionProfile2
            every { scenarioRegistry.contains("scenario-3") } returns false

            every { campaign.campaignKey } returns "my-campaign"
            every { campaign.assignments } returns listOf(
                relaxedMockk { every { scenarioName } returns "scenario-1" },
                relaxedMockk { every { scenarioName } returns "scenario-2" },
                relaxedMockk { every { scenarioName } returns "scenario-3" }
            )
            val scenarioExecutionProfileConfiguration1 = mockk<ExecutionProfileConfiguration>("profile-configuration-1")
            val scenarioExecutionProfileConfiguration2 = mockk<ExecutionProfileConfiguration>("profile-configuration-2")
            val scenarioExecutionProfileConfiguration3 = mockk<ExecutionProfileConfiguration>("profile-configuration-3")
            every { campaign.scenarios } returns mapOf(
                "scenario-1" to relaxedMockk { every { executionProfileConfiguration } returns scenarioExecutionProfileConfiguration1 },
                "scenario-2" to relaxedMockk { every { executionProfileConfiguration } returns scenarioExecutionProfileConfiguration2 },
                "scenario-3" to relaxedMockk { every { executionProfileConfiguration } returns scenarioExecutionProfileConfiguration3 }
            )
            val convertedExecutionProfile1 = mockk<ExecutionProfile>("converted-profile-1")
            val convertedExecutionProfile2 = mockk<ExecutionProfile>("converted-profile-2")
            every {
                factoryCampaignManager["convertExecutionProfile"](
                    refEq(scenarioExecutionProfileConfiguration1),
                    refEq(defaultScenarioExecutionProfile1)
                )
            } returns convertedExecutionProfile1
            every {
                factoryCampaignManager["convertExecutionProfile"](
                    refEq(scenarioExecutionProfileConfiguration2),
                    refEq(defaultScenarioExecutionProfile2)
                )
            } returns convertedExecutionProfile2

            // when
            factoryCampaignManager.init(campaign)

            // then
            verifyOnce {
                scenarioRegistry.contains("scenario-1")
                scenarioRegistry.contains("scenario-2")
                scenarioRegistry.contains("scenario-3")
                scenarioRegistry.get("scenario-1")
                factoryCampaignManager["convertExecutionProfile"](
                    refEq(scenarioExecutionProfileConfiguration1),
                    refEq(defaultScenarioExecutionProfile1)
                )
                scenarioRegistry.get("scenario-2")
                factoryCampaignManager["convertExecutionProfile"](
                    refEq(scenarioExecutionProfileConfiguration2),
                    refEq(defaultScenarioExecutionProfile2)
                )
            }
            assertThat(factoryCampaignManager).all {
                typedProp<Map<*, *>>("assignableScenariosExecutionProfiles").isEqualTo(
                    mutableMapOf(
                        "scenario-1" to convertedExecutionProfile1,
                        "scenario-2" to convertedExecutionProfile2
                    )
                )
                typedProp<String>("runningCampaign").isSameAs(campaign)
            }
            confirmVerified(
                factoryChannel,
                defaultExecutionProfile,
                minionAssignmentKeeper,
                minionsKeeper,
                scenarioRegistry
            )

            // when + then
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-1")).isTrue()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-2")).isTrue()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-3")).isFalse()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-other-campaign", "scenario-1")).isFalse()
        }

    private fun CoroutineScope.buildCampaignManager() = FactoryCampaignManagerImpl(
        minionsKeeper,
        scenarioRegistry,
        minionAssignmentKeeper,
        factoryChannel,
        sharedStateRegistry,
        Optional.of(contextConsumer),
        campaignReportLiveStateRegistry,
        this
    )

    @Test
    internal fun `should ignore init the campaign when no scenario is known`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        every { campaign.campaignKey } returns "my-campaign"
        every { scenarioRegistry.contains(any()) } returns false
        every { campaign.assignments } returns listOf(
            relaxedMockk { every { scenarioName } returns "scenario-1" },
            relaxedMockk { every { scenarioName } returns "scenario-2" },
            relaxedMockk { every { scenarioName } returns "scenario-3" }
        )

        // when
        factoryCampaignManager.init(campaign)

        // then
        verifyOnce {
            scenarioRegistry.contains("scenario-1")
            scenarioRegistry.contains("scenario-2")
            scenarioRegistry.contains("scenario-3")
        }
        assertThat(factoryCampaignManager).all {
            typedProp<Map<*, *>>("assignableScenariosExecutionProfiles").isEmpty()
            typedProp<String>("runningCampaign").isNotSameAs(campaign)
        }
        confirmVerified(
            factoryChannel,
            defaultExecutionProfile,
            minionAssignmentKeeper,
            minionsKeeper,
            scenarioRegistry
        )

        // when + then
        assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-1")).isFalse()
        assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-2")).isFalse()
        assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-3")).isFalse()
        assertThat(factoryCampaignManager.isLocallyExecuted("my-other-campaign", "scenario-1")).isFalse()
    }

    @Test
    internal fun `should warmup campaign successfully`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        every { campaign.campaignKey } returns "my-campaign"
        factoryCampaignManager.runningCampaign(campaign)
        factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to mockk()))
        val scenario = relaxedMockk<Scenario>()
        every { scenarioRegistry["my-scenario"] } returns scenario

        // when
        factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")

        // then
        coVerifyOrder {
            minionAssignmentKeeper.readSchedulePlan("my-campaign", "my-scenario")
            scenarioRegistry["my-scenario"]
            scenario.start(withArg {
                assertThat(it).all {
                    prop(ScenarioStartStopConfiguration::campaignKey).isEqualTo("my-campaign")
                    prop(ScenarioStartStopConfiguration::campaignReportLiveStateRegistry).isSameAs(
                        campaignReportLiveStateRegistry
                    )
                }
            })
            contextConsumer.start()
        }
        confirmVerified(
            factoryChannel,
            defaultExecutionProfile,
            minionAssignmentKeeper,
            minionsKeeper,
            scenarioRegistry
        )
    }

    @Test
    internal fun `should ignore warmup campaign when the running campaign is different`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            every { campaign.campaignKey } returns "my-other-campaign"
            factoryCampaignManager.runningCampaign(campaign)
            factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to mockk()))

            // when
            factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")

            // then
            confirmVerified(
                factoryChannel,
                defaultExecutionProfile,
                minionAssignmentKeeper,
                minionsKeeper,
                scenarioRegistry
            )
        }

    @Test
    internal fun `should ignore warmup campaign when the scenario is not running`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        every { campaign.campaignKey } returns "my-campaign"
        factoryCampaignManager.runningCampaign(campaign)
        factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-other-scenario" to mockk()))

        // when
        factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")

        // then
        confirmVerified(
            factoryChannel,
            defaultExecutionProfile,
            minionAssignmentKeeper,
            minionsKeeper,
            scenarioRegistry
        )
    }

    @Test
    internal fun `should warmup campaign with failure`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        every { campaign.campaignKey } returns "my-campaign"
        factoryCampaignManager.runningCampaign(campaign)
        factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to mockk()))
        val exception = RuntimeException()
        val scenario = relaxedMockk<Scenario> {
            coEvery { start(any()) } throws exception
        }
        every { scenarioRegistry["my-scenario"] } returns scenario

        // when
        val thrown = assertThrows<Throwable> {
            factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")
        }

        // then
        assertThat(thrown).isSameAs(exception)
        coVerifyOrder {
            minionAssignmentKeeper.readSchedulePlan("my-campaign", "my-scenario")
            scenarioRegistry["my-scenario"]
            scenario.start(withArg {
                assertThat(it).all {
                    prop(ScenarioStartStopConfiguration::campaignKey).isEqualTo("my-campaign")
                    prop(ScenarioStartStopConfiguration::campaignReportLiveStateRegistry).isSameAs(
                        campaignReportLiveStateRegistry
                    )
                }
            })
        }
        confirmVerified(
            factoryChannel,
            defaultExecutionProfile,
            minionAssignmentKeeper,
            minionsKeeper,
            scenarioRegistry
        )
    }

    @Test
    @Timeout(3)
    internal fun `should throw exception when minions to start on next starting line is negative`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val executionProfileConfiguration = RegularExecutionProfileConfiguration(
                periodInMs = 1000,
                minionsCountProLaunch = 10
            )
            factoryCampaignManager.runningCampaign(mockk {
                every { speedFactor } returns 3.0
                every { startOffsetMs } returns 3000
            })
            factoryCampaignManager.assignableScenariosExecutionProfiles()["my-scenario"] = defaultExecutionProfile

            coEvery { minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario") } returns 28
            every { defaultExecutionProfile.iterator(28, 3.0) } returns relaxedMockk {
                every { hasNext() } returns true
                every { next() } returns MinionsStartingLine(-1, 500)
            }
            excludeRecords { defaultExecutionProfile.toString() }

            // when
            val exception = assertThrows<IllegalArgumentException> {
                factoryCampaignManager.prepareMinionsExecutionProfile(
                    "my-campaign",
                    "my-scenario",
                    executionProfileConfiguration
                )
            }

            // then
            assertThat(exception.message).isEqualTo("The number of minions to start at next starting line cannot be negative, but was -1")
            coVerifyOrder {
                minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario")
                defaultExecutionProfile.iterator(28, 3.0)
                defaultExecutionProfile.notifyStart(3.0)
            }
            confirmVerified(minionAssignmentKeeper, defaultExecutionProfile)
        }

    @Test
    @Timeout(3)
    internal fun `should throw exception when next start is in the past`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        val executionProfileConfiguration = RegularExecutionProfileConfiguration(
            periodInMs = 1000,
            minionsCountProLaunch = 10
        )
        val campaignStartOffsetMs = 3000L
        factoryCampaignManager.runningCampaign(mockk {
            every { speedFactor } returns 2.0
            every { startOffsetMs } returns campaignStartOffsetMs
        })
        factoryCampaignManager.assignableScenariosExecutionProfiles()["my-scenario"] = defaultExecutionProfile

        coEvery { minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario") } returns 28
        every { defaultExecutionProfile.iterator(28, 2.0) } returns relaxedMockk {
            every { hasNext() } returns true
            every { next() } returns MinionsStartingLine(100, -1 - campaignStartOffsetMs)
        }
        excludeRecords { defaultExecutionProfile.toString() }

        // when
        val exception = assertThrows<IllegalArgumentException> {
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                "my-scenario",
                executionProfileConfiguration
            )
        }

        // then
        assertThat(exception.message).isNotNull()
            .matches(Regex("The next starting line should not be in the past, but was planned [0-9]+ ms ago"))
        coVerifyOrder {
            minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario")
            defaultExecutionProfile.iterator(28, 2.0)
            defaultExecutionProfile.notifyStart(2.0)
        }
        confirmVerified(minionAssignmentKeeper, defaultExecutionProfile)
    }

    @Test
    @Timeout(3)
    internal fun `should create the start definition of all the minions`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        val executionProfileConfiguration = DefaultExecutionProfileConfiguration()
        factoryCampaignManager.runningCampaign(mockk {
            every { speedFactor } returns 2.0
            every { startOffsetMs } returns 2000
        })
        factoryCampaignManager.assignableScenariosExecutionProfiles()["my-scenario"] = defaultExecutionProfile
        coEvery { minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario") } returns 28

        val minionsStartingLines = listOf(
            MinionsStartingLine(12, 500),
            MinionsStartingLine(10, 800),
            MinionsStartingLine(6, 1200)
        )
        val executionProfileIterator = relaxedMockk<ExecutionProfileIterator> {
            every { hasNext() } returns true
            every { next() } returnsMany minionsStartingLines
        }
        every { defaultExecutionProfile.iterator(28, 2.0) } returns executionProfileIterator
        excludeRecords { defaultExecutionProfile.toString() }

        // when
        val minionsStartDefinitions =
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                "my-scenario",
                executionProfileConfiguration
            )

        // then
        assertThat(minionsStartDefinitions).all {
            hasSize(3)
            containsExactly(
                MinionsStartingLine(count = 12, offsetMs = 500),
                MinionsStartingLine(count = 10, offsetMs = 1300),
                MinionsStartingLine(count = 6, offsetMs = 2500)
            )
        }

        coVerifyOrder {
            minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario")
            defaultExecutionProfile.iterator(28, 2.0)
            defaultExecutionProfile.notifyStart(2.0)
            executionProfileIterator.hasNext()
            executionProfileIterator.next()
            executionProfileIterator.hasNext()
            executionProfileIterator.next()
            executionProfileIterator.hasNext()
            executionProfileIterator.next()
        }
        confirmVerified(minionAssignmentKeeper, defaultExecutionProfile, executionProfileIterator)
    }

    @Test
    @Timeout(3)
    internal fun `should create the start definition of all the minions even when the execution profile schedules too many starts`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val campaignStartOffsetMs = 2000L
            factoryCampaignManager.assignableScenariosExecutionProfiles()["my-scenario"] = defaultExecutionProfile
            factoryCampaignManager.runningCampaign(mockk {
                every { speedFactor } returns 2.0
                every { startOffsetMs } returns campaignStartOffsetMs
            })
            val executionProfileConfiguration = DefaultExecutionProfileConfiguration()
            coEvery { minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario") } returns 28

            val minionsStartingLines = listOf(
                MinionsStartingLine(12, 500),
                MinionsStartingLine(100, 800)
            )
            val executionProfileIterator = relaxedMockk<ExecutionProfileIterator> {
                every { hasNext() } returns true
                every { next() } returnsMany minionsStartingLines
            }
            every { defaultExecutionProfile.iterator(28, 2.0) } returns executionProfileIterator
            excludeRecords { defaultExecutionProfile.toString() }

            // when
            val minionsStartDefinitions =
                factoryCampaignManager.prepareMinionsExecutionProfile(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    executionProfileConfiguration = executionProfileConfiguration
                )

            // then
            assertThat(minionsStartDefinitions).all {
                hasSize(2)
                containsExactly(
                    MinionsStartingLine(count = 12, offsetMs = 500),
                    MinionsStartingLine(count = 16, offsetMs = 1300)
                )
            }

            coVerifyOrder {
                minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario")
                defaultExecutionProfile.iterator(28, 2.0)
                defaultExecutionProfile.notifyStart(2.0)
                executionProfileIterator.hasNext()
                executionProfileIterator.next()
                executionProfileIterator.hasNext()
                executionProfileIterator.next()
            }
            confirmVerified(minionAssignmentKeeper, defaultExecutionProfile, executionProfileIterator)
        }

    @Test
    @Timeout(3)
    internal fun `should not create the start definition when there are no starting lines`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val executionProfileConfiguration = DefaultExecutionProfileConfiguration()
            factoryCampaignManager.runningCampaign(mockk {
                every { speedFactor } returns 2.0
                every { startOffsetMs } returns 2000
            })
            factoryCampaignManager.assignableScenariosExecutionProfiles()["my-scenario"] = defaultExecutionProfile
            coEvery { minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario") } returns 28

            val executionProfileIterator = relaxedMockk<ExecutionProfileIterator> {
                every { hasNext() } returns false
            }
            every { defaultExecutionProfile.iterator(28, 2.0) } returns executionProfileIterator
            excludeRecords { defaultExecutionProfile.toString() }

            // when
            val exception = assertThrows<AssertionError> {
                factoryCampaignManager.prepareMinionsExecutionProfile(
                    "my-campaign",
                    "my-scenario",
                    executionProfileConfiguration
                )
            }

            // then
            assertThat(exception.message).isNotNull().isEqualTo("28 minions could not be scheduled")

            coVerifyOrder {
                minionAssignmentKeeper.countMinionsUnderLoad("my-campaign", "my-scenario")
                defaultExecutionProfile.iterator(28, 2.0)
                defaultExecutionProfile.notifyStart(2.0)
                executionProfileIterator.hasNext()
            }
            confirmVerified(minionAssignmentKeeper, defaultExecutionProfile, executionProfileIterator)
        }

    @Test
    @Timeout(3)
    internal fun `should mark the dag complete for the minion but not restart`() = testCoroutineDispatcher.runTest {
        val factoryCampaignManager = buildCampaignManager()
        factoryCampaignManager.runningCampaign(relaxedMockk {
            every { softTimeout } returns Instant.MAX
        })
        coEvery {
            minionAssignmentKeeper.executionComplete(
                "my-campaign",
                "my-scenario",
                "my-minion",
                listOf("my-dag"),
                false
            )
        } returns CampaignCompletionState()
        factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to defaultExecutionProfile))
        every { defaultExecutionProfile.canReplay(any()) } returns true
        val minionStart = Instant.now().minusSeconds(12)

        // when
        factoryCampaignManager.notifyCompleteMinion(
            minionId = "my-minion",
            minionStart = minionStart,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            dagId = "my-dag"
        )

        // then
        coVerifyOnce {
            minionsKeeper.get("my-minion")
            defaultExecutionProfile.canReplay(withArg { assertThat(it).isLessThan(Duration.ofSeconds(13)) })
            minionAssignmentKeeper.executionComplete(
                "my-campaign",
                "my-scenario",
                "my-minion",
                listOf("my-dag"),
                true
            )
        }
        confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should mark the dag complete for the minion but not notify the minion completion when it is a singleton`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            factoryCampaignManager.runningCampaign(relaxedMockk {
                every { softTimeout } returns Instant.MAX
            })
            every { minionsKeeper[any()].isSingleton } returns true
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
            } returns CampaignCompletionState(minionComplete = true)
            factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to defaultExecutionProfile))
            every { defaultExecutionProfile.canReplay(any()) } returns false
            val minionStart = Instant.now().minusSeconds(12)

            // when
            factoryCampaignManager.notifyCompleteMinion(
                minionId = "my-minion",
                minionStart = minionStart,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                dagId = "my-dag"
            )

            // then
            coVerifyOrder {
                minionsKeeper["my-minion"]
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
                minionsKeeper.shutdownMinion("my-minion")
            }
            confirmVerified(
                factoryChannel,
                defaultExecutionProfile,
                minionAssignmentKeeper,
                minionsKeeper
            )
        }

    @Test
    @Timeout(3)
    internal fun `should mark the dag complete for the minion and notify the minion completion but not restart`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            factoryCampaignManager.runningCampaign(relaxedMockk {
                every { softTimeout } returns Instant.MAX
            })
            every { minionsKeeper[any()].isSingleton } returns false
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
            } returns CampaignCompletionState(minionComplete = true)
            factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to defaultExecutionProfile))
            every { defaultExecutionProfile.canReplay(any()) } returns false
            val minionStart = Instant.now().minusSeconds(12)

            // when
            factoryCampaignManager.notifyCompleteMinion(
                minionId = "my-minion",
                minionStart = minionStart,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                dagId = "my-dag"
            )

            // then
            coVerifyOrder {
                minionsKeeper["my-minion"]
                defaultExecutionProfile.canReplay(withArg { assertThat(it).isLessThan(Duration.ofSeconds(13)) })
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
                minionsKeeper.shutdownMinion("my-minion")
            }
            confirmVerified(
                factoryChannel,
                defaultExecutionProfile,
                minionAssignmentKeeper,
                minionsKeeper
            )
        }

    @Test
    @Timeout(3)
    internal fun `should mark the dag complete for the minion and notify the minion completion and restart`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            factoryCampaignManager.runningCampaign(relaxedMockk {
                every { softTimeout } returns Instant.now().plusSeconds(13)
            })
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    true
                )
            } returns CampaignCompletionState(minionComplete = true)
            factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to defaultExecutionProfile))
            every { defaultExecutionProfile.canReplay(any()) } returns true
            val minionStart = Instant.now().minusSeconds(12)

            // when
            factoryCampaignManager.notifyCompleteMinion(
                minionId = "my-minion",
                minionStart = minionStart,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                dagId = "my-dag"
            )

            // then
            coVerifyOrder {
                minionsKeeper.get("my-minion")
                defaultExecutionProfile.canReplay(withArg { assertThat(it).isLessThan(Duration.ofSeconds(13)) })
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    true
                )
                minionsKeeper.restartMinion("my-minion")
            }
            confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
        }


    @Test
    @Timeout(3)
    internal fun `should mark the dag complete for the minion and notify the minion completion and not restart when the campaign is closed to the timeout`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            factoryCampaignManager.runningCampaign(relaxedMockk {
                every { softTimeout } returns Instant.now().plusSeconds(11)
            })
            every { minionsKeeper[any()].isSingleton } returns false
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
            } returns CampaignCompletionState(minionComplete = true)
            factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to defaultExecutionProfile))
            every { defaultExecutionProfile.canReplay(any()) } returns true
            val minionStart = Instant.now().minusSeconds(12)

            // when
            factoryCampaignManager.notifyCompleteMinion(
                minionId = "my-minion",
                minionStart = minionStart,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                dagId = "my-dag"
            )

            // then
            coVerifyOrder {
                minionsKeeper.get("my-minion")
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
                minionsKeeper.shutdownMinion("my-minion")
            }
            confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    @Timeout(3)
    internal fun `should mark the dag complete for the minion and notify scenario completion`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            factoryCampaignManager.runningCampaign(relaxedMockk {
                every { softTimeout } returns Instant.MAX
            })
            every { minionsKeeper[any()].isSingleton } returns false
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
            } returns CampaignCompletionState(minionComplete = true, scenarioComplete = true)
            factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to defaultExecutionProfile))
            every { defaultExecutionProfile.canReplay(any()) } returns false
            val minionStart = Instant.now().minusSeconds(12)

            // when
            factoryCampaignManager.notifyCompleteMinion(
                minionId = "my-minion",
                minionStart = minionStart,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                dagId = "my-dag"
            )

            // then
            coVerifyOrder {
                minionsKeeper.get("my-minion")
                defaultExecutionProfile.canReplay(withArg { assertThat(it).isLessThan(Duration.ofSeconds(13)) })
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
                minionsKeeper.shutdownMinion("my-minion")
                factoryChannel.publishFeedback(
                    EndOfCampaignScenarioFeedback(
                        "my-campaign",
                        "my-scenario",
                        FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(
                factoryChannel,
                defaultExecutionProfile,
                minionAssignmentKeeper,
                minionsKeeper
            )
        }

    @Test
    @Timeout(3)
    internal fun `should mark the dag complete for the minion and notify the minion and scenario completions while ignoring the campaign one`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            every { minionsKeeper[any()].isSingleton } returns false
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
            } returns CampaignCompletionState(minionComplete = true, scenarioComplete = true, campaignComplete = true)
            factoryCampaignManager.assignableScenariosExecutionProfiles(mutableMapOf("my-scenario" to defaultExecutionProfile))
            val minionStart = Instant.now().minusSeconds(12)

            // when
            factoryCampaignManager.notifyCompleteMinion(
                minionId = "my-minion",
                minionStart = minionStart,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                dagId = "my-dag"
            )

            // then
            coVerifyOrder {
                minionsKeeper.get("my-minion")
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag"),
                    false
                )
                minionsKeeper.shutdownMinion("my-minion")
                factoryChannel.publishFeedback(
                    EndOfCampaignScenarioFeedback(
                        "my-campaign",
                        "my-scenario",
                        FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    @Timeout(3)
    internal fun `should shutdown the minions concurrently`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        val countLatch = SuspendedCountLatch(3)
        coEvery { minionsKeeper.shutdownMinion(any()) } coAnswers { countLatch.decrement() }

        // when
        factoryCampaignManager.shutdownMinions("my-campaign", listOf("minion-1", "minion-2", "minion-3"))
        countLatch.await()

        // then
        coVerifyOnce {
            sharedStateRegistry.clear(listOf("minion-1", "minion-2", "minion-3"))
            minionsKeeper.shutdownMinion("minion-1")
            minionsKeeper.shutdownMinion("minion-2")
            minionsKeeper.shutdownMinion("minion-3")
        }
    }

    @Test
    @Timeout(3)
    internal fun `should shutdown the minions concurrently even in case of failure`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val countLatch = SuspendedCountLatch(3)
            coEvery { minionsKeeper.shutdownMinion(any()) } coAnswers { countLatch.decrement() }
            coEvery { minionsKeeper.shutdownMinion("minion-2") } coAnswers {
                countLatch.decrement()
                throw RuntimeException()
            }

            // when
            factoryCampaignManager.shutdownMinions("my-campaign", listOf("minion-1", "minion-2", "minion-3"))
            countLatch.await()

            // then
            coVerifyOnce {
                sharedStateRegistry.clear(listOf("minion-1", "minion-2", "minion-3"))
                minionsKeeper.shutdownMinion("minion-1")
                minionsKeeper.shutdownMinion("minion-2")
                minionsKeeper.shutdownMinion("minion-3")
            }
        }

    @Test
    @Timeout(2)
    internal fun `should shutdown the minions concurrently even in case of timeout`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = FactoryCampaignManagerImpl(
                minionsKeeper,
                scenarioRegistry,
                minionAssignmentKeeper,
                factoryChannel,
                sharedStateRegistry,
                Optional.of(contextConsumer),
                campaignReportLiveStateRegistry,
                this,
                minionGracefulShutdown = Duration.ofMillis(5)
            )
            val countLatch = SuspendedCountLatch(3)
            coEvery { minionsKeeper.shutdownMinion(any()) } coAnswers { countLatch.decrement() }
            coEvery { minionsKeeper.shutdownMinion("minion-2") } coAnswers {
                countLatch.decrement()
                delay(10000)
            }

            // when
            factoryCampaignManager.shutdownMinions("my-campaign", listOf("minion-1", "minion-2", "minion-3"))
            countLatch.await()

            // then
            coVerifyOnce {
                sharedStateRegistry.clear(listOf("minion-1", "minion-2", "minion-3"))
                minionsKeeper.shutdownMinion("minion-1")
                minionsKeeper.shutdownMinion("minion-2")
                minionsKeeper.shutdownMinion("minion-3")
            }
        }


    @Test
    @Timeout(3)
    internal fun `should shutdown the scenario even if the context consumer throws an exception`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val scenario = relaxedMockk<Scenario>()
            every { scenarioRegistry["my-scenario"] } returns scenario
            coEvery { contextConsumer.stop() } throws RuntimeException()

            // when
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")

            // then
            coVerifyOrder {
                contextConsumer.stop()
                scenario.stop(withArg {
                    assertThat(it).all {
                        prop(ScenarioStartStopConfiguration::campaignKey).isEqualTo("my-campaign")
                        prop(ScenarioStartStopConfiguration::campaignReportLiveStateRegistry).isSameAs(
                            campaignReportLiveStateRegistry
                        )
                    }
                })
            }
            confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    @Timeout(3)
    internal fun `should shutdown the scenario`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        val scenario = relaxedMockk<Scenario>()
        every { scenarioRegistry["my-scenario"] } returns scenario

        // when
        factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")

        // then
        coVerifyOrder {
            contextConsumer.stop()
            scenario.stop(withArg {
                assertThat(it).all {
                    prop(ScenarioStartStopConfiguration::campaignKey).isEqualTo("my-campaign")
                    prop(ScenarioStartStopConfiguration::campaignReportLiveStateRegistry).isSameAs(
                        campaignReportLiveStateRegistry
                    )
                }
            })
        }
        confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(3)
    @Disabled("FIXME")
    internal fun `should shutdown the scenario and throw the timeout exception`() = testCoroutineDispatcher.runTest {
        // given
        val scenario = relaxedMockk<Scenario> { coEvery { stop(any()) } coAnswers { delay(2000) } }
        every { scenarioRegistry["my-scenario"] } returns scenario
        val factoryCampaignManager = FactoryCampaignManagerImpl(
            minionsKeeper,
            scenarioRegistry,
            minionAssignmentKeeper,
            factoryChannel,
            sharedStateRegistry,
            Optional.of(contextConsumer),
            campaignReportLiveStateRegistry,
            this,
            scenarioGracefulShutdown = Duration.ofMillis(1)
        )
        factoryCampaignManager.runningCampaign(relaxedMockk {
            every { softTimeout } returns Instant.MAX
        })

        // when
        assertThrows<TimeoutCancellationException> {
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
        }

        // then
        coVerifyOrder {
            contextConsumer.stop()
            scenario.stop(withArg {
                assertThat(it).all {
                    prop(ScenarioStartStopConfiguration::campaignKey).isEqualTo("my-campaign")
                    prop(ScenarioStartStopConfiguration::campaignReportLiveStateRegistry).isSameAs(
                        campaignReportLiveStateRegistry
                    )
                }
            })
        }
        confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should shutdown the scenario and throw the exception`() = testCoroutineDispatcher.run {
        // given
        val scenario = relaxedMockk<Scenario> {
            coEvery { stop(any()) } throws RuntimeException("There is an error")
        }
        every { scenarioRegistry["my-scenario"] } returns scenario
        val factoryCampaignManager = FactoryCampaignManagerImpl(
            minionsKeeper,
            scenarioRegistry,
            minionAssignmentKeeper,
            factoryChannel,
            sharedStateRegistry,
            Optional.of(contextConsumer),
            campaignReportLiveStateRegistry,
            this
        )

        // when
        val exception = assertThrows<RuntimeException> {
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
        }

        // then
        assertThat(exception.message).isEqualTo("There is an error")
        coVerifyOrder {
            contextConsumer.stop()
            scenario.stop(withArg {
                assertThat(it).all {
                    prop(ScenarioStartStopConfiguration::campaignKey).isEqualTo("my-campaign")
                    prop(ScenarioStartStopConfiguration::campaignReportLiveStateRegistry).isSameAs(
                        campaignReportLiveStateRegistry
                    )
                }
            })
        }
        confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should ignore to shutdown an unknown scenario`() = testCoroutineDispatcher.runTest {
        // when
        val factoryCampaignManager = buildCampaignManager()
        assertDoesNotThrow {
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
        }

        // then
        confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should shutdown the whole campaign`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        val scenario1 = relaxedMockk<Scenario>()
        val scenario2 = relaxedMockk<Scenario>()
        every { campaign.campaignKey } returns "my-campaign"
        factoryCampaignManager.runningCampaign(campaign)
        factoryCampaignManager.assignableScenariosExecutionProfiles(
            mutableMapOf(
                "my-scenario-1" to mockk(),
                "my-scenario-2" to mockk()
            )
        )
        every { scenarioRegistry["my-scenario-1"] } returns scenario1
        every { scenarioRegistry["my-scenario-2"] } returns scenario2

        // when
        factoryCampaignManager.close(campaign)

        // then
        coVerifyOnce {
            minionsKeeper.shutdownAll()
            scenario1.stop(withArg {
                assertThat(it).all {
                    prop(ScenarioStartStopConfiguration::campaignKey).isEqualTo("my-campaign")
                    prop(ScenarioStartStopConfiguration::campaignReportLiveStateRegistry).isSameAs(
                        campaignReportLiveStateRegistry
                    )
                }
            })
            scenario2.stop(withArg {
                assertThat(it).all {
                    prop(ScenarioStartStopConfiguration::campaignKey).isEqualTo("my-campaign")
                    prop(ScenarioStartStopConfiguration::campaignReportLiveStateRegistry).isSameAs(
                        campaignReportLiveStateRegistry
                    )
                }
            })
            sharedStateRegistry.clear()
        }
        assertThat(factoryCampaignManager).all {
            typedProp<Map<*, *>>("assignableScenariosExecutionProfiles").isEmpty()
            typedProp<String>("runningCampaign").isNotSameAs(campaign)
        }
        confirmVerified(
            factoryChannel,
            defaultExecutionProfile,
            minionAssignmentKeeper,
            minionsKeeper,
            sharedStateRegistry
        )
    }

    @Test
    @Timeout(3)
    internal fun `should ignore to shutdown an unknown campaign`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        factoryCampaignManager.runningCampaign(campaign)

        // when
        assertDoesNotThrow {
            factoryCampaignManager.close(relaxedMockk {
                every { campaignKey } returns "my-other-campaign"
            })
        }

        // then
        confirmVerified(factoryChannel, defaultExecutionProfile, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should convert execution profile configuration to execution profile`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()

            // when
            var executionProfile = factoryCampaignManager.convertExecutionProfile(
                RegularExecutionProfileConfiguration(
                    periodInMs = 1000,
                    minionsCountProLaunch = 10
                ),
                defaultExecutionProfile
            )

            // then
            assertThat(executionProfile).isDataClassEqualTo(
                RegularExecutionProfile(
                    periodInMs = 1000,
                    minionsCountProLaunch = 10
                )
            )

            // when
            executionProfile = factoryCampaignManager.convertExecutionProfile(
                ImmediateExecutionProfileConfiguration(),
                defaultExecutionProfile
            )

            // then
            assertThat(executionProfile).isInstanceOf<ImmediateExecutionProfile>()

            // when
            executionProfile = factoryCampaignManager.convertExecutionProfile(
                AcceleratingExecutionProfileConfiguration(
                    startPeriodMs = 764,
                    accelerator = 123.5,
                    minPeriodMs = 234,
                    minionsCountProLaunch = 2365
                ),
                defaultExecutionProfile
            )

            // then
            assertThat(executionProfile).isEqualToIgnoringGivenProperties(
                AcceleratingExecutionProfile(
                    startPeriodMs = 764,
                    accelerator = 123.5,
                    minPeriodMs = 234,
                    minionsCountProLaunch = 2365
                )
            )

            // when
            executionProfile = factoryCampaignManager.convertExecutionProfile(
                ProgressiveVolumeExecutionProfileConfiguration(
                    periodMs = 764,
                    minionsCountProLaunchAtStart = 123,
                    multiplier = 234.5,
                    maxMinionsCountProLaunch = 2365
                ),
                defaultExecutionProfile
            )

            // then
            assertThat(executionProfile).isEqualToIgnoringGivenProperties(
                ProgressiveVolumeExecutionProfile(
                    periodMs = 764,
                    minionsCountProLaunchAtStart = 123,
                    multiplier = 234.5,
                    maxMinionsCountProLaunch = 2365
                )
            )

            // when
            executionProfile = factoryCampaignManager.convertExecutionProfile(
                TimeFrameExecutionProfileConfiguration(periodInMs = 764, timeFrameInMs = 564),
                defaultExecutionProfile
            )

            // then
            assertThat(executionProfile).isEqualToIgnoringGivenProperties(
                TimeFrameExecutionProfile(periodInMs = 764, timeFrameInMs = 564),
            )

            // when
            executionProfile = factoryCampaignManager.convertExecutionProfile(
                StageExecutionProfileConfiguration(GRACEFUL, Stage(12, 234, 75464, 12), Stage(75, 4433, 46456, 343)),
                defaultExecutionProfile
            )

            // then
            assertThat(executionProfile).isEqualToIgnoringGivenProperties(
                StageExecutionProfile(
                    GRACEFUL,
                    listOf(
                        io.qalipsis.api.executionprofile.Stage(
                            minionsCount = 12,
                            rampUpDurationMs = 234,
                            totalDurationMs = 75464,
                            resolutionMs = 12
                        ),
                        io.qalipsis.api.executionprofile.Stage(
                            minionsCount = 75,
                            rampUpDurationMs = 4433,
                            totalDurationMs = 46456,
                            resolutionMs = 343
                        )
                    )
                ),
            )

            // when
            executionProfile = factoryCampaignManager.convertExecutionProfile(
                PercentageStageExecutionProfileConfiguration(
                    GRACEFUL,
                    PercentageStage(12.0, 234, 75464, 12),
                    PercentageStage(75.3, 4433, 46456, 343)
                ),
                defaultExecutionProfile
            )

            // then
            assertThat(executionProfile).isEqualToIgnoringGivenProperties(
                PercentageStageExecutionProfile(
                    GRACEFUL,
                    listOf(
                        io.qalipsis.api.executionprofile.PercentageStage(
                            minionsPercentage = 12.0,
                            rampUpDurationMs = 234,
                            totalDurationMs = 75464,
                            resolutionMs = 12
                        ),
                        io.qalipsis.api.executionprofile.PercentageStage(
                            minionsPercentage = 75.3,
                            rampUpDurationMs = 4433,
                            totalDurationMs = 46456,
                            resolutionMs = 343
                        )
                    )
                ),
            )
        }

    @Test
    @Timeout(3)
    internal fun `should convert execution profile configuration to execution profile when configuration is default`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val executionProfileConfiguration = DefaultExecutionProfileConfiguration()

            // when
            val executionProfile = factoryCampaignManager.convertExecutionProfile(
                executionProfileConfiguration,
                defaultExecutionProfile
            )

            // then
            assertThat(executionProfile).isEqualTo(this@FactoryCampaignManagerImplTest.defaultExecutionProfile)
        }

}