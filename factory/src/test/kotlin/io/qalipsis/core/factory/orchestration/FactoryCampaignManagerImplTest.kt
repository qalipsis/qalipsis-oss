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
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.executionprofile.ExecutionProfileIterator
import io.qalipsis.api.executionprofile.MinionsStartingLine
import io.qalipsis.api.executionprofile.RegularExecutionProfile
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.catadioptre.convertExecutionProfile
import io.qalipsis.core.factory.orchestration.catadioptre.runningCampaign
import io.qalipsis.core.factory.orchestration.catadioptre.runningScenarios
import io.qalipsis.core.factory.steps.ContextConsumer
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import java.time.Duration
import java.util.Optional
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

@WithMockk
internal class FactoryCampaignManagerImplTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var executionProfile: ExecutionProfile

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    @RelaxedMockK
    private lateinit var sharedStateRegistry: SharedStateRegistry

    @RelaxedMockK
    private lateinit var contextConsumer: ContextConsumer

    @RelaxedMockK
    private lateinit var campaign: Campaign

    @RelaxedMockK
    private lateinit var contextConsumerOptional: Optional<ContextConsumer>

    @BeforeEach
    internal fun setUp() {
        every { contextConsumerOptional.get() } returns contextConsumer
    }

    @Test
    internal fun `should init the campaign for the factory and return whether the scenario is run locally`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            every { scenarioRegistry.contains("scenario-1") } returns true
            every { scenarioRegistry.contains("scenario-2") } returns true
            every { scenarioRegistry.contains("scenario-3") } returns false
            every { campaign.campaignKey } returns "my-campaign"
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
                typedProp<Set<*>>("runningScenarios").isEqualTo(mutableSetOf("scenario-1", "scenario-2"))
                typedProp<String>("runningCampaign").isSameAs(campaign)
            }
            confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper, scenarioRegistry)

            // when + then
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-1")).isTrue()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-2")).isTrue()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-3")).isFalse()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-other-campaign", "scenario-1")).isFalse()
        }

    private fun CoroutineScope.buildCampaignManager() = FactoryCampaignManagerImpl(
        minionsKeeper,
        meterRegistry,
        scenarioRegistry,
        minionAssignmentKeeper,
        factoryChannel,
        sharedStateRegistry,
        Optional.of(contextConsumer),
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
            typedProp<Set<*>>("runningScenarios").isEmpty()
            typedProp<String>("runningCampaign").isNotSameAs(campaign)
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper, scenarioRegistry)

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
        factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))
        val scenario = relaxedMockk<Scenario>()
        every { scenarioRegistry["my-scenario"] } returns scenario

        // when
        factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")

        // then
        coVerifyOrder {
            scenarioRegistry["my-scenario"]
            scenario.start("my-campaign")
            minionsKeeper.startSingletons("my-scenario")
            contextConsumer.start()
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper, scenarioRegistry)
    }

    @Test
    internal fun `should ignore warmup campaign when the running campaign is different`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            every { campaign.campaignKey } returns "my-other-campaign"
            factoryCampaignManager.runningCampaign(campaign)
            factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))

            // when
            factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")

            // then
            confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper, scenarioRegistry)
        }

    @Test
    internal fun `should ignore warmup campaign when the scenario is not running`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        every { campaign.campaignKey } returns "my-campaign"
        factoryCampaignManager.runningCampaign(campaign)
        factoryCampaignManager.runningScenarios(mutableSetOf("my-other-scenario"))

        // when
        factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")

        // then
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper, scenarioRegistry)
    }

    @Test
    internal fun `should warmup campaign with failure`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        every { campaign.campaignKey } returns "my-campaign"
        factoryCampaignManager.runningCampaign(campaign)
        factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))
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
            scenarioRegistry["my-scenario"]
            scenario.start("my-campaign")
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper, scenarioRegistry)
    }

    @Test
    @Timeout(1)
    internal fun `should throw exception when minions to start on next starting line is negative`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = spyk(buildCampaignManager(), recordPrivateCalls = true)
            val executionProfileConfiguration = RegularExecutionProfileConfiguration(
                periodInMs = 1000,
                minionsCountProLaunch = 10,
                speedFactor = 3.0,
                startOffsetMs = 3000
            )
            val scenario = relaxedMockk<Scenario> {
                every { name } returns "my-scenario"
                every { executionProfile } returns this@FactoryCampaignManagerImplTest.executionProfile
            }
            every {
                factoryCampaignManager["convertExecutionProfile"](
                    executionProfileConfiguration,
                    scenario.executionProfile
                )
            } returns this@FactoryCampaignManagerImplTest.executionProfile

            coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                    (0..27).map { "minion-$it" }
            every { executionProfile.iterator(28, 3.0) } returns relaxedMockk {
                every { hasNext() } returns true
                every { next() } returns MinionsStartingLine(-1, 500)
            }

            // when
            val exception = assertThrows<IllegalArgumentException> {
                factoryCampaignManager.prepareMinionsExecutionProfile(
                    "my-campaign",
                    scenario,
                    executionProfileConfiguration
                )
            }

            // then
            assertThat(exception.message).isEqualTo("The number of minions to start at next starting line cannot be negative, but was -1")
            coVerifyOrder {
                factoryCampaignManager.prepareMinionsExecutionProfile(
                    "my-campaign",
                    scenario,
                    executionProfileConfiguration
                )
                minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
                factoryCampaignManager["convertExecutionProfile"](
                    executionProfileConfiguration,
                    scenario.executionProfile
                )
                executionProfile.iterator(28, 3.0)
            }
            confirmVerified(minionAssignmentKeeper, factoryCampaignManager, executionProfile)
        }

    @Test
    @Timeout(1)
    internal fun `should throw exception when start offset is zero`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = spyk(buildCampaignManager(), recordPrivateCalls = true)
        val executionProfileConfiguration = RegularExecutionProfileConfiguration(
            periodInMs = 1000,
            minionsCountProLaunch = 10,
            speedFactor = 2.0,
            startOffsetMs = 3000
        )
        val scenario = relaxedMockk<Scenario> {
            every { name } returns "my-scenario"
            every { executionProfile } returns this@FactoryCampaignManagerImplTest.executionProfile
        }

        every {
            factoryCampaignManager["convertExecutionProfile"](
                executionProfileConfiguration,
                scenario.executionProfile
            )
        } returns this@FactoryCampaignManagerImplTest.executionProfile

        val allMinionsUnderLoad = (0..2).map { "minion-$it" }
        coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                allMinionsUnderLoad
        every { executionProfile.iterator(allMinionsUnderLoad.size, 2.0) } returns relaxedMockk {
            every { hasNext() } returns true
            every { next() } returns MinionsStartingLine(100, 0)
        }

        // when
        val exception = assertThrows<IllegalArgumentException> {
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                scenario,
                executionProfileConfiguration
            )
        }

        // then
        assertThat(exception.message).isEqualTo("The time offset of the next starting line should be strictly positive, but was 0 ms")
        coVerifyOrder {
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                scenario,
                executionProfileConfiguration
            )
            minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
            factoryCampaignManager["convertExecutionProfile"](
                executionProfileConfiguration,
                scenario.executionProfile
            )
            executionProfile.iterator(allMinionsUnderLoad.size, 2.0)
        }
        confirmVerified(minionAssignmentKeeper, factoryCampaignManager, executionProfile)
    }

    @Test
    @Timeout(1)
    internal fun `should throw exception when start offset is negative`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = spyk(buildCampaignManager(), recordPrivateCalls = true)
        val executionProfileConfiguration = RegularExecutionProfileConfiguration(
            periodInMs = 1000,
            minionsCountProLaunch = 10
        )
        val scenario = relaxedMockk<Scenario> {
            every { name } returns "my-scenario"
            every { executionProfile } returns this@FactoryCampaignManagerImplTest.executionProfile
        }
        every {
            factoryCampaignManager["convertExecutionProfile"](
                executionProfileConfiguration,
                scenario.executionProfile
            )
        } returns this@FactoryCampaignManagerImplTest.executionProfile

        val allMinionsUnderLoad = (0..9).map { "minion-$it" }
        coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                allMinionsUnderLoad
        every { executionProfile.iterator(allMinionsUnderLoad.size, 1.0) } returns relaxedMockk {
            every { hasNext() } returns true
            every { next() } returns MinionsStartingLine(100, -1)
        }

        // when
        val exception = assertThrows<IllegalArgumentException> {
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                scenario,
                executionProfileConfiguration
            )
        }

        // then
        assertThat(exception.message).isEqualTo("The time offset of the next starting line should be strictly positive, but was -1 ms")
        coVerifyOrder {
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                scenario,
                executionProfileConfiguration
            )
            minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
            factoryCampaignManager["convertExecutionProfile"](
                executionProfileConfiguration,
                scenario.executionProfile
            )
            executionProfile.iterator(allMinionsUnderLoad.size, 1.0)
        }
        confirmVerified(minionAssignmentKeeper, factoryCampaignManager, executionProfile)
    }

    @Test
    @Timeout(1)
    internal fun `should create the start definition of all the minions`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        val executionProfileConfiguration = DefaultExecutionProfileConfiguration(2000, 2.0)
        val scenario = relaxedMockk<Scenario> {
            every { name } returns "my-scenario"
            every { executionProfile } returns this@FactoryCampaignManagerImplTest.executionProfile
        }
        val allMinionsUnderLoad = (0..27).map { "minion-$it" }
        coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                allMinionsUnderLoad

        val executionProfileIterator = relaxedMockk<ExecutionProfileIterator> {
            every { hasNext() } returns true
            every { next() } returnsMany listOf(
                MinionsStartingLine(12, 500),
                MinionsStartingLine(10, 800),
                MinionsStartingLine(6, 1200)
            )
        }
        every { executionProfile.iterator(allMinionsUnderLoad.size, 2.0) } returns executionProfileIterator

        // when
        val start = System.currentTimeMillis() + 2000
        val minionsStartDefinitions =
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                scenario,
                executionProfileConfiguration
            )

        // then
        assertThat(minionsStartDefinitions).all {
            hasSize(28)
            var index = 0
            (0..11).map {
                index(index++).all {
                    prop(MinionStartDefinition::minionId).isEqualTo("minion-$it")
                    prop(MinionStartDefinition::timestamp).isBetween(start + 500, start + 600)
                }
            }
            (12..21).map {
                index(index++).all {
                    prop(MinionStartDefinition::minionId).isEqualTo("minion-$it")
                    prop(MinionStartDefinition::timestamp).isBetween(start + 1300, start + 1500)
                }
            }
            (22..27).map {
                index(index++).all {
                    prop(MinionStartDefinition::minionId).isEqualTo("minion-$it")
                    prop(MinionStartDefinition::timestamp).isBetween(start + 2500, start + 2700)
                }
            }
        }

        coVerifyOrder {
            minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
            executionProfile.iterator(28, 2.0)
            executionProfileIterator.hasNext()
            executionProfileIterator.next()
            executionProfileIterator.hasNext()
            executionProfileIterator.next()
            executionProfileIterator.hasNext()
            executionProfileIterator.next()
        }
        confirmVerified(minionAssignmentKeeper, executionProfile, executionProfileIterator)
    }

    @Test
    @Timeout(1)
    internal fun `should create the start definition of all the minions even when the execution profile schedules to many starts`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val executionProfileConfiguration = DefaultExecutionProfileConfiguration(2000, 2.0)
            val scenario = relaxedMockk<Scenario> {
                every { name } returns "my-scenario"
                every { executionProfile } returns this@FactoryCampaignManagerImplTest.executionProfile
            }
            val allMinionsUnderLoad = (0..27).map { "minion-$it" }
            coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                    allMinionsUnderLoad

            val executionProfileIterator = relaxedMockk<ExecutionProfileIterator> {
                every { hasNext() } returns true
                every { next() } returnsMany listOf(
                    MinionsStartingLine(12, 500),
                    MinionsStartingLine(100, 800)
                )
            }
            every { executionProfile.iterator(allMinionsUnderLoad.size, 2.0) } returns executionProfileIterator

            // when
            val start = System.currentTimeMillis() + 2000
            val minionsStartDefinitions =
                factoryCampaignManager.prepareMinionsExecutionProfile(
                    "my-campaign",
                    scenario,
                    executionProfileConfiguration
                )

            // then
            assertThat(minionsStartDefinitions).all {
                hasSize(28)
                var index = 0
                (0..11).map {
                    index(index++).all {
                        prop(MinionStartDefinition::minionId).isEqualTo("minion-$it")
                        prop(MinionStartDefinition::timestamp).isBetween(start + 500, start + 600)
                    }
                }
                (12..27).map {
                    index(index++).all {
                        prop(MinionStartDefinition::minionId).isEqualTo("minion-$it")
                        prop(MinionStartDefinition::timestamp).isBetween(start + 1300, start + 1500)
                    }
                }
            }

            coVerifyOrder {
                minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
                executionProfile.iterator(28, 2.0)
                executionProfileIterator.hasNext()
                executionProfileIterator.next()
                executionProfileIterator.hasNext()
                executionProfileIterator.next()
            }
            confirmVerified(minionAssignmentKeeper, executionProfile, executionProfileIterator)
        }

    @Test
    @Timeout(1)
    internal fun `should not create the start definition when there are no starting lines`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val executionProfileConfiguration = DefaultExecutionProfileConfiguration(2000, 2.0)
            val scenario = relaxedMockk<Scenario> {
                every { name } returns "my-scenario"
                every { executionProfile } returns this@FactoryCampaignManagerImplTest.executionProfile
            }
            val allMinionsUnderLoad = (0..27).map { "minion-$it" }
            coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                    allMinionsUnderLoad

            val executionProfileIterator = relaxedMockk<ExecutionProfileIterator> {
                every { hasNext() } returns false
            }
            every { executionProfile.iterator(allMinionsUnderLoad.size, 2.0) } returns executionProfileIterator

            // when
            val minionsStartDefinitions =
                factoryCampaignManager.prepareMinionsExecutionProfile(
                    "my-campaign",
                    scenario,
                    executionProfileConfiguration
                )

            // then
            assertThat(minionsStartDefinitions).all {
                hasSize(0)
            }

            coVerifyOrder {
                minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
                executionProfile.iterator(28, 2.0)
                executionProfileIterator.hasNext()
            }
            confirmVerified(minionAssignmentKeeper, executionProfile, executionProfileIterator)
        }

    @Test
    @Timeout(1)
    internal fun `should mark the dag complete for the minion`() = testCoroutineDispatcher.runTest {
        val factoryCampaignManager = buildCampaignManager()
        coEvery {
            minionAssignmentKeeper.executionComplete(
                "my-campaign",
                "my-scenario",
                "my-minion",
                listOf("my-dag")
            )
        } returns CampaignCompletionState()
        factoryCampaignManager.getProperty<MutableSet<ScenarioName>>("runningScenarios").add("my-scenario")

        // when
        factoryCampaignManager.notifyCompleteMinion("my-minion", "my-campaign", "my-scenario", "my-dag")

        // then
        coVerifyOnce {
            minionAssignmentKeeper.executionComplete(
                "my-campaign",
                "my-scenario",
                "my-minion",
                listOf("my-dag")
            )
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(1)
    @Disabled("The emission of CompleteMinionFeedback is disabled until they can be grouped to reduce the load they generate")
    internal fun `should mark the dag complete for the minion and notify the minion completion`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
            } returns CampaignCompletionState(minionComplete = true)
            factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))

            // when
            factoryCampaignManager.notifyCompleteMinion("my-minion", "my-campaign", "my-scenario", "my-dag")

            // then
            coVerifyOrder {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
            }
            confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    @Timeout(1)
    internal fun `should mark the dag complete for the minion and notify scenario completion`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
            } returns CampaignCompletionState(minionComplete = true, scenarioComplete = true)
            factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))

            // when
            factoryCampaignManager.notifyCompleteMinion("my-minion", "my-campaign", "my-scenario", "my-dag")

            // then
            coVerifyOrder {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
                factoryChannel.publishFeedback(
                    EndOfCampaignScenarioFeedback(
                        "my-campaign",
                        "my-scenario",
                        FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    @Timeout(1)
    internal fun `should mark the dag complete for the minion and notify the minion and scenario completions while ignoring the campaign one`() =
        testCoroutineDispatcher.runTest {
            val factoryCampaignManager = buildCampaignManager()
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
            } returns CampaignCompletionState(minionComplete = true, scenarioComplete = true, campaignComplete = true)
            factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))

            // when
            factoryCampaignManager.notifyCompleteMinion("my-minion", "my-campaign", "my-scenario", "my-dag")

            // then
            coVerifyOrder {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
                factoryChannel.publishFeedback(
                    EndOfCampaignScenarioFeedback(
                        "my-campaign",
                        "my-scenario",
                        FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    @Timeout(1)
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
    @Timeout(1)
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
                meterRegistry,
                scenarioRegistry,
                minionAssignmentKeeper,
                factoryChannel,
                sharedStateRegistry,
                Optional.of(contextConsumer),
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
    @Timeout(1)
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
                scenario.stop("my-campaign")
            }
            confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    @Timeout(1)
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
            scenario.stop("my-campaign")
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
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
            meterRegistry,
            scenarioRegistry,
            minionAssignmentKeeper,
            factoryChannel,
            sharedStateRegistry,
            Optional.of(contextConsumer),
            this,
            scenarioGracefulShutdown = Duration.ofMillis(1)
        )

        // when
        assertThrows<TimeoutCancellationException> {
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
        }

        // then
        coVerifyOrder {
            contextConsumer.stop()
            scenario.stop("my-campaign")
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
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
            meterRegistry,
            scenarioRegistry,
            minionAssignmentKeeper,
            factoryChannel,
            sharedStateRegistry,
            Optional.of(contextConsumer),
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
            scenario.stop("my-campaign")
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(1)
    internal fun `should ignore to shutdown an unknown scenario`() = testCoroutineDispatcher.runTest {
        // when
        val factoryCampaignManager = buildCampaignManager()
        assertDoesNotThrow {
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
        }

        // then
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(1)
    internal fun `should shutdown the whole campaign`() = testCoroutineDispatcher.runTest {
        // given
        val factoryCampaignManager = buildCampaignManager()
        val scenario1 = relaxedMockk<Scenario>()
        val scenario2 = relaxedMockk<Scenario>()
        every { campaign.campaignKey } returns "my-campaign"
        factoryCampaignManager.runningCampaign(campaign)
        factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario-1", "my-scenario-2"))
        every { scenarioRegistry["my-scenario-1"] } returns scenario1
        every { scenarioRegistry["my-scenario-2"] } returns scenario2

        // when
        factoryCampaignManager.close(campaign)

        // then
        coVerifyOnce {
            minionsKeeper.shutdownAll()
            scenario1.stop("my-campaign")
            scenario2.stop("my-campaign")
            meterRegistry.clear()
            sharedStateRegistry.clear()
        }
        assertThat(factoryCampaignManager).all {
            typedProp<Set<*>>("runningScenarios").isEmpty()
            typedProp<String>("runningCampaign").isNotSameAs(campaign)
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper, sharedStateRegistry)
    }

    @Test
    @Timeout(1)
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
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    @Timeout(1)
    internal fun `should convert execution profile configuration to execution profile`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val executionProfileConfiguration = RegularExecutionProfileConfiguration(
                periodInMs = 1000,
                minionsCountProLaunch = 10,
                speedFactor = 3.0,
                startOffsetMs = 3000
            )
            val scenario = relaxedMockk<Scenario> {
                every { name } returns "my-scenario"
                every { executionProfile } returns this@FactoryCampaignManagerImplTest.executionProfile
            }
            val expectedExecutionProfile = RegularExecutionProfile(
                executionProfileConfiguration.periodInMs,
                executionProfileConfiguration.minionsCountProLaunch
            )

            // when
            val executionProfile = factoryCampaignManager.convertExecutionProfile(
                executionProfileConfiguration,
                scenario.executionProfile
            )

            // then
            assertThat(executionProfile).isEqualTo(expectedExecutionProfile)
        }

    @Test
    @Timeout(1)
    internal fun `should convert execution profile configuration to execution profile when configuration is default`() =
        testCoroutineDispatcher.runTest {
            // given
            val factoryCampaignManager = buildCampaignManager()
            val executionProfileConfiguration = DefaultExecutionProfileConfiguration()
            val scenario = relaxedMockk<Scenario> {
                every { name } returns "my-scenario"
                every { executionProfile } returns this@FactoryCampaignManagerImplTest.executionProfile
            }

            // when
            val executionProfile = factoryCampaignManager.convertExecutionProfile(
                executionProfileConfiguration,
                scenario.executionProfile
            )

            // then
            assertThat(executionProfile).isEqualTo(this@FactoryCampaignManagerImplTest.executionProfile)
        }

}