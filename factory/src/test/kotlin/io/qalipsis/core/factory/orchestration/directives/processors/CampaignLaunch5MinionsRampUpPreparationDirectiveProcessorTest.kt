package io.qalipsis.core.factory.orchestration.directives.processors

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.directives.MinionsRampUpPreparationDirectiveReference
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.rampup.RampUpConfiguration
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

@WithMockk
internal class CampaignLaunch5MinionsRampUpPreparationDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var directiveProducer: DirectiveProducer

    @RelaxedMockK
    private lateinit var directiveRegistry: DirectiveRegistry

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var processor: CampaignLaunch5MinionsRampUpPreparationDirectiveProcessor

    @BeforeEach
    internal fun setUp() {
        every { idGenerator.short() } returnsMany (1..3).map { "the-key-$it" }
        every { factoryCampaignManager.feedbackNodeId } returns "my-factory"
        every { factoryConfiguration.directiveRegistry.broadcastDirectivesChannel } returns "broadcast-channel"
    }

    @Test
    @Timeout(2)
    internal fun `should accept MinionsRampUpPreparationDirectiveReference`() {
        val directiveReference =
            MinionsRampUpPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", "broadcast")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        assertTrue(processor.accept(directiveReference))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
        confirmVerified(factoryCampaignManager)
    }

    @Test
    @Timeout(2)
    internal fun `should not accept not MinionsRampUpPreparationDirectiveReference`() {
        assertFalse(processor.accept(TestDescriptiveDirective()))
        confirmVerified(factoryCampaignManager)
    }

    @Test
    @Timeout(2)
    internal fun `should not accept MinionsRampUpPreparationDirectiveReference for unknown scenario`() {
        val directiveReference =
            MinionsRampUpPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", "broadcast")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        assertFalse(processor.accept(directiveReference))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(2)
    internal fun `should not process when the directive was already read`() = testCoroutineDispatcher.run {
        val directiveReference =
            MinionsRampUpPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", "broadcast")
        every { scenarioRegistry.get("my-scenario") } returns relaxedMockk()
        coEvery { directiveRegistry.read(refEq(directiveReference)) } returns null

        // when
        processor.process(directiveReference)

        // then
        coVerifyOrder {
            scenarioRegistry.get("my-scenario")
            directiveRegistry.read(refEq(directiveReference))
        }

        confirmVerified(
            scenarioRegistry,
            directiveProducer,
            directiveRegistry,
            feedbackFactoryChannel,
            factoryConfiguration,
            idGenerator,
            factoryCampaignManager
        )
    }

    @Test
    @Timeout(2)
    internal fun `should process the directive when not already read`() = testCoroutineDispatcher.run {
        val directiveReference =
            MinionsRampUpPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", "broadcast")
        val scenario = relaxedMockk<Scenario> {
            every { id } returns "my-scenario"
        }
        every { scenarioRegistry.get("my-scenario") } returns scenario
        val rampUpConfiguration = relaxedMockk<RampUpConfiguration>()
        coEvery { directiveRegistry.read(refEq(directiveReference)) } returns rampUpConfiguration
        val minionsStartDefinitions = relaxedMockk<List<MinionStartDefinition>> { }
        coEvery {
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, refEq(rampUpConfiguration))
        } returns minionsStartDefinitions
        every { factoryConfiguration.directiveRegistry.broadcastDirectivesChannel } returns "broadcast-channel"

        // when
        processor.process(directiveReference)

        // then
        coVerifyOrder {
            scenarioRegistry["my-scenario"]
            directiveRegistry.read(refEq(directiveReference))
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsRampUpPreparationFeedback(
                    key = "the-key-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, refEq(rampUpConfiguration))
            factoryConfiguration.directiveRegistry.broadcastDirectivesChannel
            directiveProducer.publish(
                MinionsStartDirective(
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    minionsStartDefinitions,
                    channel = "broadcast-channel",
                    key = "the-key-2"
                )
            )
            feedbackFactoryChannel.publish(
                MinionsRampUpPreparationFeedback(
                    key = "the-key-3",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }

        confirmVerified(
            scenarioRegistry,
            directiveProducer,
            directiveRegistry,
            feedbackFactoryChannel,
            factoryConfiguration,
            factoryCampaignManager
        )
    }

    @Test
    @Timeout(2)
    internal fun `should fail to process the directive when not already read`() = testCoroutineDispatcher.run {
        val directiveReference =
            MinionsRampUpPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", "broadcast")
        val scenario = relaxedMockk<Scenario> {
            every { id } returns "my-scenario"
        }
        every { scenarioRegistry["my-scenario"] } returns scenario
        val rampUpConfiguration = relaxedMockk<RampUpConfiguration>()
        coEvery { directiveRegistry.read(refEq(directiveReference)) } returns rampUpConfiguration
        coEvery {
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, refEq(rampUpConfiguration))
        } throws RuntimeException("A problem occurred")

        // when
        processor.process(directiveReference)

        // then
        coVerifyOrder {
            scenarioRegistry["my-scenario"]
            directiveRegistry.read(refEq(directiveReference))
            factoryCampaignManager.feedbackNodeId
            feedbackFactoryChannel.publish(
                MinionsRampUpPreparationFeedback(
                    key = "the-key-1",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, refEq(rampUpConfiguration))
            feedbackFactoryChannel.publish(
                MinionsRampUpPreparationFeedback(
                    key = "the-key-2",
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    nodeId = "my-factory",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }

        confirmVerified(
            scenarioRegistry,
            directiveProducer,
            directiveRegistry,
            feedbackFactoryChannel,
            factoryConfiguration,
            factoryCampaignManager
        )
    }
}