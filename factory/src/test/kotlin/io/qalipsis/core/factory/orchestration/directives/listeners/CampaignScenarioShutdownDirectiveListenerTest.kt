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
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignScenarioShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignScenarioShutdownDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @InjectMockKs
    private lateinit var processor: CampaignScenarioShutdownDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept campaign scenario shutdown directive`() {
        val directive = CampaignScenarioShutdownDirective("my-campaign", "my-scenario", "")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept campaign scenario shutdown directive when the scenario is not executed locally`() {
        val directive = CampaignScenarioShutdownDirective("my-campaign", "my-scenario", "")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept not campaign scenario shutdown directive`() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    fun `should process the directive and confirm when all is right`() = testCoroutineDispatcher.runTest {
        val directive = CampaignScenarioShutdownDirective("my-campaign", "my-scenario", "")

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                CampaignScenarioShutdownFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
            factoryChannel.publishFeedback(
                CampaignScenarioShutdownFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(factoryCampaignManager, factoryChannel)
    }

    @Test
    fun `should process the directive and fails when there is an exception`() =
        testCoroutineDispatcher.runTest {
            val directive = CampaignScenarioShutdownDirective("my-campaign", "my-scenario", "")
            coEvery {
                factoryCampaignManager.shutdownScenario(
                    any(),
                    any()
                )
            } throws RuntimeException("A problem occurred")

            // when
            processor.notify(directive)

            // then
            coVerifyOrder {
                factoryChannel.publishFeedback(
                    CampaignScenarioShutdownFeedback(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario",
                        status = FeedbackStatus.IN_PROGRESS
                    )
                )
                factoryCampaignManager.shutdownScenario("my-campaign", "my-scenario")
                factoryChannel.publishFeedback(
                    CampaignScenarioShutdownFeedback(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario",
                        status = FeedbackStatus.FAILED,
                        error = "A problem occurred"
                    )
                )
            }
            confirmVerified(factoryCampaignManager, factoryChannel)
        }
}