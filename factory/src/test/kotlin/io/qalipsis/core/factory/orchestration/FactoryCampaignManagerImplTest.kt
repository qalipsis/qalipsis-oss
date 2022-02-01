package io.qalipsis.core.factory.orchestration

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.rampup.MinionsStartingLine
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.rampup.RampUpStrategyIterator
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.directives.FactoryCampaignManagerImpl
import io.qalipsis.core.factory.orchestration.directives.catadioptre.runningCampaign
import io.qalipsis.core.factory.orchestration.directives.catadioptre.runningScenarios
import io.qalipsis.core.feedbacks.CompleteMinionFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.rampup.RampUpConfiguration
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.BeforeEach
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
    private lateinit var rampUpStrategy: RampUpStrategy

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    @InjectMockKs
    private lateinit var factoryCampaignManager: FactoryCampaignManagerImpl

    @BeforeEach
    internal fun setUp() {
        factoryCampaignManager.runningCampaign("")
        factoryCampaignManager.runningScenarios(mutableSetOf())
        every { factoryConfiguration.nodeId } returns "my-factory"
    }

    @Test
    internal fun `should init the campaign for the factory and return whether the scenario is run locally`() =
        testCoroutineDispatcher.runTest {
            // given
            every { scenarioRegistry.contains("scenario-1") } returns true
            every { scenarioRegistry.contains("scenario-2") } returns true
            every { scenarioRegistry.contains("scenario-3") } returns false

            // when
            factoryCampaignManager.initCampaign("my-campaign", listOf("scenario-1", "scenario-2", "scenario-3"))

            // then
            verifyOnce {
                scenarioRegistry.contains("scenario-1")
                scenarioRegistry.contains("scenario-2")
                scenarioRegistry.contains("scenario-3")
            }
            assertThat(factoryCampaignManager).all {
                typedProp<Set<*>>("runningScenarios").isEqualTo(mutableSetOf("scenario-1", "scenario-2"))
                typedProp<String>("runningCampaign").isEqualTo("my-campaign")
            }
            confirmVerified(
                feedbackFactoryChannel,
                minionAssignmentKeeper,
                minionsKeeper,
                scenarioRegistry,
                feedbackFactoryChannel
            )

            // when + then
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-1")).isTrue()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-2")).isTrue()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-campaign", "scenario-3")).isFalse()
            assertThat(factoryCampaignManager.isLocallyExecuted("my-other-campaign", "scenario-1")).isFalse()
        }

    @Test
    internal fun `should ignore init the campaign when no scenario is known`() = testCoroutineDispatcher.runTest {
        // given
        every { scenarioRegistry.contains(any()) } returns false

        // when
        factoryCampaignManager.initCampaign("my-campaign", listOf("scenario-1", "scenario-2", "scenario-3"))

        // then
        verifyOnce {
            scenarioRegistry.contains("scenario-1")
            scenarioRegistry.contains("scenario-2")
            scenarioRegistry.contains("scenario-3")
        }
        assertThat(factoryCampaignManager).all {
            typedProp<Set<*>>("runningScenarios").isEmpty()
            typedProp<String>("runningCampaign").isEmpty()
        }
        confirmVerified(
            feedbackFactoryChannel,
            minionAssignmentKeeper,
            minionsKeeper,
            scenarioRegistry,
            feedbackFactoryChannel
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
        factoryCampaignManager.runningCampaign("my-campaign")
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
        }
        confirmVerified(
            feedbackFactoryChannel,
            minionAssignmentKeeper,
            minionsKeeper,
            scenarioRegistry,
            feedbackFactoryChannel
        )
    }

    @Test
    internal fun `should ignore warmup campaign when the running campaign is different`() =
        testCoroutineDispatcher.runTest {
            // given
            factoryCampaignManager.runningCampaign("my-other-campaign")
            factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))

            // when
            factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")

            // then
            confirmVerified(
                feedbackFactoryChannel,
                minionAssignmentKeeper,
                minionsKeeper,
                scenarioRegistry,
                feedbackFactoryChannel
            )
        }

    @Test
    internal fun `should ignore warmup campaign when the scenario is not running`() = testCoroutineDispatcher.runTest {
        // given
        factoryCampaignManager.runningCampaign("my-campaign")
        factoryCampaignManager.runningScenarios(mutableSetOf("my-other-scenario"))

        // when
        factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")

        // then
        confirmVerified(
            feedbackFactoryChannel,
            minionAssignmentKeeper,
            minionsKeeper,
            scenarioRegistry,
            feedbackFactoryChannel
        )
    }

    @Test
    internal fun `should warmup campaign with failure`() = testCoroutineDispatcher.runTest {
        // given
        factoryCampaignManager.runningCampaign("my-campaign")
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
        confirmVerified(
            feedbackFactoryChannel,
            minionAssignmentKeeper,
            minionsKeeper,
            scenarioRegistry,
            feedbackFactoryChannel
        )
    }

    @Test
    @Timeout(1)
    internal fun `should throw exception when minions to start on next starting line is negative`() =
        testCoroutineDispatcher.runTest {
            // given
            val rampUpConfiguration = RampUpConfiguration(2000, 3.0)
            val scenario = relaxedMockk<Scenario> {
                every { id } returns "my-scenario"
                every { rampUpStrategy } returns this@FactoryCampaignManagerImplTest.rampUpStrategy
            }
            coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                    (0..27).map { "minion-$it" }
            every { rampUpStrategy.iterator(28, 3.0) } returns relaxedMockk {
                every { next() } returns MinionsStartingLine(-1, 500)
            }

            // when
            val exception = assertThrows<IllegalArgumentException> {
                factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, rampUpConfiguration)
            }

            // then
            assertThat(exception.message).isEqualTo("The number of minions to start at next starting line cannot be negative, but was -1")
            coVerifyOrder {
                minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
            }
            confirmVerified(minionAssignmentKeeper)
        }

    @Test
    @Timeout(1)
    internal fun `should throw exception when start offset is zero`() = testCoroutineDispatcher.runTest {
        // given
        val rampUpConfiguration = RampUpConfiguration(2000, 2.0)
        val scenario = relaxedMockk<Scenario> {
            every { id } returns "my-scenario"
            every { rampUpStrategy } returns this@FactoryCampaignManagerImplTest.rampUpStrategy
        }
        val allMinionsUnderLoad = (0..2).map { "minion-$it" }
        coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                allMinionsUnderLoad
        every { rampUpStrategy.iterator(allMinionsUnderLoad.size, 2.0) } returns relaxedMockk {
            every { next() } returns MinionsStartingLine(100, 0)
        }

        // when
        val exception = assertThrows<IllegalArgumentException> {
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, rampUpConfiguration)
        }

        // then
        assertThat(exception.message).isEqualTo("The time offset of the next starting line should be strictly positive, but was 0 ms")
        coVerifyOrder {
            minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
        }
        confirmVerified(minionAssignmentKeeper)
    }

    @Test
    @Timeout(1)
    internal fun `should throw exception when start offset is negative`() = testCoroutineDispatcher.runTest {
        // given
        val rampUpConfiguration = RampUpConfiguration(2000, 1.0)
        val scenario = relaxedMockk<Scenario> {
            every { id } returns "my-scenario"
            every { rampUpStrategy } returns this@FactoryCampaignManagerImplTest.rampUpStrategy
        }
        val allMinionsUnderLoad = (0..9).map { "minion-$it" }
        coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                allMinionsUnderLoad
        every { rampUpStrategy.iterator(allMinionsUnderLoad.size, 1.0) } returns relaxedMockk {
            every { next() } returns MinionsStartingLine(100, -1)
        }

        // when
        val exception = assertThrows<IllegalArgumentException> {
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, rampUpConfiguration)
        }

        // then
        assertThat(exception.message).isEqualTo("The time offset of the next starting line should be strictly positive, but was -1 ms")
        coVerifyOrder {
            minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario")
        }
        confirmVerified(minionAssignmentKeeper)
    }

    @Test
    @Timeout(1)
    internal fun `should create the start definition of all the minions`() = testCoroutineDispatcher.runTest {
        // given
        val rampUpConfiguration = RampUpConfiguration(2000, 2.0)
        val scenario = relaxedMockk<Scenario> {
            every { id } returns "my-scenario"
            every { rampUpStrategy } returns this@FactoryCampaignManagerImplTest.rampUpStrategy
        }
        val allMinionsUnderLoad = (0..27).map { "minion-$it" }
        coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                allMinionsUnderLoad

        val rampUpStrategyIterator = relaxedMockk<RampUpStrategyIterator> {
            every { next() } returnsMany listOf(
                MinionsStartingLine(12, 500),
                MinionsStartingLine(10, 800),
                MinionsStartingLine(6, 1200)
            )
        }
        every { rampUpStrategy.iterator(allMinionsUnderLoad.size, 2.0) } returns rampUpStrategyIterator

        // when
        val start = System.currentTimeMillis() + 2000
        val minionsStartDefinitions =
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, rampUpConfiguration)

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
            rampUpStrategy.iterator(28, 2.0)
            rampUpStrategyIterator.next()
            rampUpStrategyIterator.next()
            rampUpStrategyIterator.next()
        }
        confirmVerified(minionAssignmentKeeper, rampUpStrategy, rampUpStrategyIterator)
    }

    @Test
    @Timeout(1)
    internal fun `should create the start definition of all the minions even when the ramp-up strategy schedules to many starts`() =
        testCoroutineDispatcher.runTest {
            // given
            val rampUpConfiguration = RampUpConfiguration(2000, 2.0)
            val scenario = relaxedMockk<Scenario> {
                every { id } returns "my-scenario"
                every { rampUpStrategy } returns this@FactoryCampaignManagerImplTest.rampUpStrategy
            }
            val allMinionsUnderLoad = (0..27).map { "minion-$it" }
            coEvery { minionAssignmentKeeper.getIdsOfMinionsUnderLoad("my-campaign", "my-scenario") } returns
                    allMinionsUnderLoad

            val rampUpStrategyIterator = relaxedMockk<RampUpStrategyIterator> {
                every { next() } returnsMany listOf(
                    MinionsStartingLine(12, 500),
                    MinionsStartingLine(100, 800)
                )
            }
            every { rampUpStrategy.iterator(allMinionsUnderLoad.size, 2.0) } returns rampUpStrategyIterator

            // when
            val start = System.currentTimeMillis() + 2000
            val minionsStartDefinitions =
                factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, rampUpConfiguration)

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
                rampUpStrategy.iterator(28, 2.0)
                rampUpStrategyIterator.next()
                rampUpStrategyIterator.next()
            }
            confirmVerified(minionAssignmentKeeper, rampUpStrategy, rampUpStrategyIterator)
        }

    @Test
    internal fun `should mark the dag complete for the minion`() = testCoroutineDispatcher.runTest {
        coEvery {
            minionAssignmentKeeper.executionComplete(
                "my-campaign",
                "my-scenario",
                "my-minion",
                listOf("my-dag")
            )
        } returns CampaignCompletionState()
        factoryCampaignManager.getProperty<MutableSet<ScenarioId>>("runningScenarios").add("my-scenario")

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
        confirmVerified(feedbackFactoryChannel, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    internal fun `should mark the dag complete for the minion and notify the minion completion`() =
        testCoroutineDispatcher.runTest {
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
            } returns CampaignCompletionState(minionComplete = true)
            factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))

            every { idGenerator.short() } returns "1"

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
                feedbackFactoryChannel.publish(
                    CompleteMinionFeedback(
                        "1",
                        "my-campaign",
                        "my-scenario",
                        "my-minion",
                        "my-factory",
                        FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(feedbackFactoryChannel, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    internal fun `should mark the dag complete for the minion and notify the minion and scenario completions`() =
        testCoroutineDispatcher.runTest {
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
            } returns CampaignCompletionState(minionComplete = true, scenarioComplete = true)
            factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))
            every { idGenerator.short() } returnsMany listOf("1", "2")

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
                feedbackFactoryChannel.publish(
                    CompleteMinionFeedback(
                        "1",
                        "my-campaign",
                        "my-scenario",
                        "my-minion",
                        "my-factory",
                        FeedbackStatus.COMPLETED
                    )
                )
                feedbackFactoryChannel.publish(
                    EndOfCampaignScenarioFeedback(
                        "2",
                        "my-campaign",
                        "my-scenario",
                        "my-factory",
                        FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(feedbackFactoryChannel, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    internal fun `should mark the dag complete for the minion and notify the minion and scenario and campaign completions`() =
        testCoroutineDispatcher.runTest {
            coEvery {
                minionAssignmentKeeper.executionComplete(
                    "my-campaign",
                    "my-scenario",
                    "my-minion",
                    listOf("my-dag")
                )
            } returns CampaignCompletionState(minionComplete = true, scenarioComplete = true, campaignComplete = true)
            factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario"))
            every { idGenerator.short() } returnsMany listOf("1", "2", "3")

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
                feedbackFactoryChannel.publish(
                    CompleteMinionFeedback(
                        "1",
                        "my-campaign",
                        "my-scenario",
                        "my-minion",
                        "my-factory",
                        FeedbackStatus.COMPLETED
                    )
                )
                feedbackFactoryChannel.publish(
                    EndOfCampaignScenarioFeedback(
                        "2",
                        "my-campaign",
                        "my-scenario",
                        "my-factory",
                        FeedbackStatus.COMPLETED
                    )
                )
                feedbackFactoryChannel.publish(
                    EndOfCampaignFeedback(
                        "3",
                        "my-campaign",
                        "my-factory",
                        FeedbackStatus.COMPLETED
                    )
                )
            }
            confirmVerified(feedbackFactoryChannel, minionAssignmentKeeper, minionsKeeper)
        }

    @Test
    internal fun `should shutdown the scenario`() = testCoroutineDispatcher.runTest {
        // given
        val scenario = relaxedMockk<Scenario>()
        every { scenarioRegistry["my-scenario"] } returns scenario

        // when
        factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")

        // then
        coVerifyOnce {
            scenario.stop("my-campaign")
        }
        confirmVerified(feedbackFactoryChannel, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    internal fun `should ignore to shutdown an unknown scenario`() = testCoroutineDispatcher.runTest {
        // when
        assertDoesNotThrow {
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
        }

        // then
        confirmVerified(feedbackFactoryChannel, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    internal fun `should shutdown the whole campaign`() = testCoroutineDispatcher.runTest {
        // given
        val scenario1 = relaxedMockk<Scenario>()
        val scenario2 = relaxedMockk<Scenario>()
        factoryCampaignManager.runningCampaign("my-campaign")
        factoryCampaignManager.runningScenarios(mutableSetOf("my-scenario-1", "my-scenario-2"))
        every { scenarioRegistry["my-scenario-1"] } returns scenario1
        every { scenarioRegistry["my-scenario-2"] } returns scenario2

        // when
        factoryCampaignManager.shutdownCampaign("my-campaign")

        // then
        coVerifyOnce {
            minionsKeeper.shutdownAll()
            scenario1.stop("my-campaign")
            scenario2.stop("my-campaign")
            meterRegistry.clear()
        }
        assertThat(factoryCampaignManager).all {
            typedProp<Set<*>>("runningScenarios").isEmpty()
            typedProp<String>("runningCampaign").isEmpty()
        }
        confirmVerified(feedbackFactoryChannel, minionAssignmentKeeper, minionsKeeper)
    }

    @Test
    internal fun `should ignore to shutdown an unknown campaign`() = testCoroutineDispatcher.runTest {
        // given
        factoryCampaignManager.runningCampaign("my-other-campaign")

        // when
        assertDoesNotThrow {
            factoryCampaignManager.shutdownCampaign("my-campaign")
        }

        // then
        confirmVerified(feedbackFactoryChannel, minionAssignmentKeeper, minionsKeeper)
    }
}