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
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.rampup.MinionsStartingLine
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.rampup.RampUpStrategyIterator
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.catadioptre.runningCampaign
import io.qalipsis.core.factory.orchestration.catadioptre.runningScenarios
import io.qalipsis.core.factory.steps.ContextConsumer
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.rampup.RampUpConfiguration
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
import java.util.Optional

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
            every { campaign.campaignName } returns "my-campaign"
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
        every { campaign.campaignName } returns "my-campaign"
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
        every { campaign.campaignName } returns "my-campaign"
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
            every { campaign.campaignName } returns "my-other-campaign"
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
        every { campaign.campaignName } returns "my-campaign"
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
        every { campaign.campaignName } returns "my-campaign"
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
            val factoryCampaignManager = buildCampaignManager()
            val rampUpConfiguration = RampUpConfiguration(2000, 3.0)
            val scenario = relaxedMockk<Scenario> {
                every { name } returns "my-scenario"
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
        val factoryCampaignManager = buildCampaignManager()
        val rampUpConfiguration = RampUpConfiguration(2000, 2.0)
        val scenario = relaxedMockk<Scenario> {
            every { name } returns "my-scenario"
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
        val factoryCampaignManager = buildCampaignManager()
        val rampUpConfiguration = RampUpConfiguration(2000, 1.0)
        val scenario = relaxedMockk<Scenario> {
            every { name } returns "my-scenario"
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
        val factoryCampaignManager = buildCampaignManager()
        val rampUpConfiguration = RampUpConfiguration(2000, 2.0)
        val scenario = relaxedMockk<Scenario> {
            every { name } returns "my-scenario"
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
            val factoryCampaignManager = buildCampaignManager()
            val rampUpConfiguration = RampUpConfiguration(2000, 2.0)
            val scenario = relaxedMockk<Scenario> {
                every { name } returns "my-scenario"
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
        every { campaign.campaignName } returns "my-campaign"
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
                every { campaignName } returns "my-other-campaign"
            })
        }

        // then
        confirmVerified(factoryChannel, minionAssignmentKeeper, minionsKeeper)
    }
}