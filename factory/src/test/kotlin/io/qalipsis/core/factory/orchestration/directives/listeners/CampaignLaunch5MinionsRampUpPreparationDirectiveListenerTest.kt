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
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
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
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @InjectMockKs
    private lateinit var processor: CampaignLaunch5MinionsRampUpPreparationDirectiveListener

    @Test
    @Timeout(2)
    internal fun `should accept MinionsRampUpPreparationDirectiveReference`() {
        val directive =
            MinionsRampUpPreparationDirectiveReference(
                "my-directive", "my-campaign", "my-scenario"
            )
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
            MinionsRampUpPreparationDirectiveReference(
                "my-directive", "my-campaign", "my-scenario"
            )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        assertFalse(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(2)
    internal fun `should process the directive when not already read`() = testCoroutineDispatcher.run {
        val executionProfileConfiguration = relaxedMockk<ExecutionProfileConfiguration>()
        val directive =
            MinionsRampUpPreparationDirective("my-campaign", "my-scenario", executionProfileConfiguration, "")
        val minionsStartDefinitions = (1..650).map { relaxedMockk<MinionStartDefinition>() }
        coEvery {
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                "my-scenario",
                refEq(executionProfileConfiguration)
            )
        } returns minionsStartDefinitions

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsRampUpPreparationFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                "my-scenario",
                refEq(executionProfileConfiguration)
            )
            factoryChannel.publishDirective(
                MinionsStartDirective(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    minionsStartDefinitions.subList(0, 400)
                )
            )
            factoryChannel.publishDirective(
                MinionsStartDirective(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    minionsStartDefinitions.subList(400, 650)
                )
            )
            factoryChannel.publishFeedback(
                MinionsRampUpPreparationFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }

        confirmVerified(factoryChannel, factoryCampaignManager)
    }

    @Test
    @Timeout(2)
    internal fun `should fail to process the directive when not already read`() = testCoroutineDispatcher.run {
        val executionProfileConfiguration = relaxedMockk<ExecutionProfileConfiguration>()
        val directive =
            MinionsRampUpPreparationDirective("my-campaign", "my-scenario", executionProfileConfiguration, "")
        val scenario = relaxedMockk<Scenario> {
            every { name } returns "my-scenario"
        }
        coEvery {
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                "my-scenario",
                refEq(executionProfileConfiguration)
            )
        } throws RuntimeException("A problem occurred")

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsRampUpPreparationFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.prepareMinionsExecutionProfile(
                "my-campaign",
                "my-scenario",
                refEq(executionProfileConfiguration)
            )
            factoryChannel.publishFeedback(
                MinionsRampUpPreparationFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.FAILED,
                    errorMessage = "A problem occurred"
                )
            )
        }

        confirmVerified(factoryChannel, factoryCampaignManager)
    }
}