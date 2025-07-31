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
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignAbortDirectiveListenerTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @InjectMockKs
    private lateinit var listener: CampaignAbortDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept campaign abort directive`() {
        val directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "broadcast",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortRunningCampaign = AbortRunningCampaign()
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign") } returns true

        Assertions.assertTrue(listener.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept campaign abort directive when the campaign is not executed locally`() {
        val directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "broadcast",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortRunningCampaign = AbortRunningCampaign()
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign") } returns false

        Assertions.assertFalse(listener.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept not campaign abort directive`() {
        Assertions.assertFalse(listener.accept(TestDescriptiveDirective()))
    }

    @Test
    fun `should process the directive and confirm when all is right`() = testCoroutineDispatcher.runTest {
        val directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "broadcast",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortRunningCampaign = AbortRunningCampaign()
        )

        // when
        listener.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                CampaignAbortFeedback(
                    campaignKey = "my-campaign",
                    status = FeedbackStatus.IN_PROGRESS,
                    scenarioNames = listOf("my-scenario-1", "my-scenario-2")
                )
            )
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario-1")
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario-2")
            factoryChannel.publishFeedback(
                CampaignAbortFeedback(
                    campaignKey = "my-campaign",
                    status = FeedbackStatus.COMPLETED,
                    scenarioNames = listOf("my-scenario-1", "my-scenario-2")
                )
            )
        }
        confirmVerified(factoryCampaignManager, factoryChannel)
    }

    @Test
    fun `should process the directive and fail when there is an exception`() = testCoroutineDispatcher.runTest {
        val directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "broadcast",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortRunningCampaign = AbortRunningCampaign()
        )
        coEvery { factoryCampaignManager.shutdownScenario(any(), any()) } throws RuntimeException("A problem occurred")

        // when
        listener.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                CampaignAbortFeedback(
                    campaignKey = "my-campaign",
                    status = FeedbackStatus.IN_PROGRESS,
                    scenarioNames = listOf("my-scenario-1", "my-scenario-2")
                )
            )
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario-1")
            factoryChannel.publishFeedback(
                CampaignAbortFeedback(
                    campaignKey = "my-campaign",
                    status = FeedbackStatus.FAILED,
                    scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
                    errorMessage = "A problem occurred"
                )
            )
        }
        confirmVerified(factoryCampaignManager, factoryChannel)
    }
}