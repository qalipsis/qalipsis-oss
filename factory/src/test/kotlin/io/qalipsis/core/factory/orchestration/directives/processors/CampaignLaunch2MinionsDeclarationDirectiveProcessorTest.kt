package io.qalipsis.core.factory.orchestration.directives.processors

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.directives.MinionsDeclarationDirectiveReference
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.factory.testDag
import io.qalipsis.core.factory.testScenario
import io.qalipsis.core.feedbacks.DirectiveFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignLaunch2MinionsDeclarationDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var directiveRegistry: DirectiveRegistry

    @RelaxedMockK
    private lateinit var directiveProducer: DirectiveProducer

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var processor: CampaignLaunch2MinionsDeclarationDirectiveProcessor

    @BeforeEach
    internal fun setUp() {
        every { idGenerator.short() } returnsMany (1..3).map { "the-key-$it" }
        every { factoryCampaignManager.feedbackNodeId } returns "my-factory"
    }

    @Test
    @Timeout(1)
    fun `should accept minions creation preparation directive`() {
        val directive = MinionsDeclarationDirectiveReference(
            "my-directive",
            "my-campaign",
            "my-scenario",
            channel = "broadcast"
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        assertTrue(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
        confirmVerified(factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept not minions creation preparation directive`() {
        assertFalse(processor.accept(TestDescriptiveDirective()))
        confirmVerified(factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept minions creation preparation directive for unknown scenario`() {
        val directive = MinionsDeclarationDirectiveReference(
            "my-directive",
            "my-campaign",
            "my-scenario",
            channel = "broadcast"
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        assertFalse(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(1)
    fun `should not process when scenario not found`() = testCoroutineDispatcher.runTest {
        val directive = MinionsDeclarationDirectiveReference(
            "my-directive",
            "my-campaign",
            "my-scenario",
            channel = "broadcast"
        )
        every { scenarioRegistry.get("my-scenario") } returns null

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            scenarioRegistry["my-scenario"]
        }

        confirmVerified(directiveRegistry, scenarioRegistry, feedbackFactoryChannel, directiveProducer)
    }

    @Test
    @Timeout(1)
    fun `should not process when scenario is found but the directive was already processed`() =
        testCoroutineDispatcher.runTest {
            val directive = MinionsDeclarationDirectiveReference(
                "my-directive",
                "my-campaign",
                "my-scenario",
                channel = "broadcast"
            )
            every { scenarioRegistry.get("my-scenario") } returns relaxedMockk()
            coEvery { directiveRegistry.read(refEq(directive)) } returns null

            // when
            processor.process(directive)

            // then
            coVerifyOrder {
                scenarioRegistry.get("my-scenario")
                directiveRegistry.read(refEq(directive))
            }

            confirmVerified(directiveRegistry, scenarioRegistry, feedbackFactoryChannel, directiveProducer)
        }

    @Test
    @Timeout(5)
    fun `should declare and register all the minions for the scenario`() = testCoroutineDispatcher.runTest {
        // given
        val directive = MinionsDeclarationDirectiveReference(
            "my-directive",
            "my-campaign",
            "my-scenario",
            channel = "broadcast"
        )
        coEvery { directiveRegistry.read(refEq(directive)) } returns 123
        val scenario = testScenario("my-scenario", minionsCount = 2) {
            this.createIfAbsent("my-dag-1") { testDag("my-dag-1", this, isUnderLoad = true) }
            this.createIfAbsent("my-dag-2") {
                testDag("my-dag-2", this, isUnderLoad = true, isSingleton = true)
            }
            this.createIfAbsent("my-dag-3") {
                testDag("my-dag-3", this, isUnderLoad = false, isSingleton = false)
            }
        }
        every { factoryConfiguration.directiveRegistry.broadcastDirectivesChannel } returns "the-broadcast-channel"
        every { scenarioRegistry.get("my-scenario") } returns scenario
        val assignmentDirectiveSlot = slot<MinionsAssignmentDirective>()
        coEvery { directiveProducer.publish(capture(assignmentDirectiveSlot)) } returns Unit
        val feedbacks = mutableListOf<DirectiveFeedback>()
        coEvery { feedbackFactoryChannel.publish(capture(feedbacks)) } returns Unit

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            scenarioRegistry["my-scenario"]
            directiveRegistry.read(refEq(directive))
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsDeclarationFeedback(
                    key = "the-key-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                "my-campaign",
                "my-scenario",
                listOf("my-dag-2"),
                match { it.size == 1 && it.first().startsWith("my-scenario-lonely-") },
                false
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                "my-campaign",
                "my-scenario",
                listOf("my-dag-3"),
                match { it.size == 1 && it.first().startsWith("my-scenario-lonely-") },
                false
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                "my-campaign",
                "my-scenario",
                listOf("my-dag-1"),
                match { with(it) { size == 123 && all { it.startsWith("my-scenario-") } && none { it.startsWith("my-scenario-lonely-") } } },
                true
            )
            minionAssignmentKeeper.completeUnassignedMinionsRegistration("my-campaign", "my-scenario")
            directiveProducer.publish(any())
            feedbackFactoryChannel.publish(
                MinionsDeclarationFeedback(
                    key = "the-key-3",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        assertThat(assignmentDirectiveSlot.captured).all {
            prop(MinionsAssignmentDirective::scenarioId).isEqualTo("my-scenario")
            prop(MinionsAssignmentDirective::campaignId).isEqualTo("my-campaign")
            prop(MinionsAssignmentDirective::channel).isEqualTo("the-broadcast-channel")
        }

        confirmVerified(
            directiveRegistry,
            scenarioRegistry,
            feedbackFactoryChannel,
            directiveProducer,
            minionAssignmentKeeper
        )
    }

    @Test
    @Timeout(5)
    fun `should fail to declare and register all the minions for the scenario`() = testCoroutineDispatcher.runTest {
        // given
        val directive = MinionsDeclarationDirectiveReference(
            "my-directive",
            "my-campaign",
            "my-scenario",
            channel = "broadcast"
        )
        coEvery { directiveRegistry.read(refEq(directive)) } returns 123
        val scenario = testScenario("my-scenario", minionsCount = 2) {
            this.createIfAbsent("my-dag-1") { testDag("my-dag-1", this, isUnderLoad = true) }
            this.createIfAbsent("my-dag-2") {
                testDag("my-dag-2", this, isUnderLoad = true, isSingleton = true)
            }
            this.createIfAbsent("my-dag-3") {
                testDag("my-dag-3", this, isUnderLoad = false, isSingleton = false)
            }
        }
        every { scenarioRegistry.get("my-scenario") } returns scenario
        coEvery {
            minionAssignmentKeeper.registerMinionsToAssign(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } throws RuntimeException("A problem occurred")

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            scenarioRegistry.get("my-scenario")
            directiveRegistry.read(refEq(directive))
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsDeclarationFeedback(
                    key = "the-key-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionAssignmentKeeper.registerMinionsToAssign(any(), any(), any(), any(), any())
            feedbackFactoryChannel.publish(
                MinionsDeclarationFeedback(
                    key = "the-key-3",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }

        confirmVerified(
            directiveRegistry,
            scenarioRegistry,
            feedbackFactoryChannel,
            directiveProducer,
            minionAssignmentKeeper
        )
    }
}
