package io.qalipsis.core.factory.orchestration.directives.listeners

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsRampUpPreparationDirectiveReference
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.rampup.RampUpConfiguration
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignLaunch5MinionsRampUpPreparationDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @InjectMockKs
    private lateinit var processor: CampaignLaunch5MinionsRampUpPreparationDirectiveListener

    @Test
    @Timeout(2)
    internal fun `should accept MinionsRampUpPreparationDirectiveReference`() {
        val directive =
            MinionsRampUpPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        assertTrue(processor.accept(directive))

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
        val directive =
            MinionsRampUpPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        assertFalse(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(2)
    internal fun `should process the directive when not already read`() = testCoroutineDispatcher.run {
        val rampUpConfiguration = relaxedMockk<RampUpConfiguration>()
        val directive =
            MinionsRampUpPreparationDirective("my-campaign", "my-scenario", rampUpConfiguration, "")
        val scenario = relaxedMockk<Scenario> {
            every { id } returns "my-scenario"
        }
        every { scenarioRegistry.get("my-scenario") } returns scenario
        val minionsStartDefinitions = (1..650).map { relaxedMockk<MinionStartDefinition>() }
        coEvery {
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, refEq(rampUpConfiguration))
        } returns minionsStartDefinitions

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsRampUpPreparationFeedback(
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            scenarioRegistry["my-scenario"]
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, refEq(rampUpConfiguration))
            factoryChannel.publishDirective(
                MinionsStartDirective(
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    minionsStartDefinitions.subList(0, 400)
                )
            )
            factoryChannel.publishDirective(
                MinionsStartDirective(
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    minionsStartDefinitions.subList(400, 650)
                )
            )
            factoryChannel.publishFeedback(
                MinionsRampUpPreparationFeedback(
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }

        confirmVerified(scenarioRegistry, factoryChannel, factoryCampaignManager)
    }

    @Test
    @Timeout(2)
    internal fun `should fail to process the directive when not already read`() = testCoroutineDispatcher.run {
        val rampUpConfiguration = relaxedMockk<RampUpConfiguration>()
        val directive =
            MinionsRampUpPreparationDirective("my-campaign", "my-scenario", rampUpConfiguration, "")
        val scenario = relaxedMockk<Scenario> {
            every { id } returns "my-scenario"
        }
        every { scenarioRegistry["my-scenario"] } returns scenario
        coEvery {
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, refEq(rampUpConfiguration))
        } throws RuntimeException("A problem occurred")

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsRampUpPreparationFeedback(
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            scenarioRegistry["my-scenario"]
            factoryCampaignManager.prepareMinionsRampUp("my-campaign", scenario, refEq(rampUpConfiguration))
            factoryChannel.publishFeedback(
                MinionsRampUpPreparationFeedback(
                    campaignId = "my-campaign",
                    scenarioId = "my-scenario",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }

        confirmVerified(scenarioRegistry, factoryChannel, factoryCampaignManager)
    }
}