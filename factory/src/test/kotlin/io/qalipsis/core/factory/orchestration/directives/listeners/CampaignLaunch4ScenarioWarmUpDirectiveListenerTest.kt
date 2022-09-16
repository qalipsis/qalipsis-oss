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
import io.mockk.verify
import io.mockk.verifyOrder
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignLaunch4ScenarioWarmUpDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @InjectMockKs
    private lateinit var processor: CampaignLaunch4ScenarioWarmUpDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept campaign warm-up directive`() {
        val directive = ScenarioWarmUpDirective("my-campaign", "my-scenario", "")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        assertTrue(processor.accept(directive))
        verifyOrder {
            factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario")
        }
        confirmVerified(localAssignmentStore, factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept not campaign warm-up directive`() {
        assertFalse(processor.accept(TestDescriptiveDirective()))

        confirmVerified(localAssignmentStore, factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept campaign warm-up directive for unknown scenario`() {
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", "")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        assertFalse(processor.accept(directive))

        verify { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
        confirmVerified(localAssignmentStore, factoryCampaignManager)
    }

    @Test
    internal fun `should warm up the campaign successfully`() = testCoroutineDispatcher.runTest {
        // given
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", "")
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns true

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            localAssignmentStore.hasMinionsAssigned("my-scenario")
            factoryChannel.publishFeedback(
                ScenarioWarmUpFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")
            factoryChannel.publishFeedback(
                ScenarioWarmUpFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(localAssignmentStore, factoryChannel, factoryCampaignManager)
    }


    @Test
    internal fun `should ignore when no minion is locally assigned`() = testCoroutineDispatcher.runTest {
        // given
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", "")
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns false

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            localAssignmentStore.hasMinionsAssigned("my-scenario")
            factoryChannel.publishFeedback(
                ScenarioWarmUpFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IGNORED
                )
            )
        }
        confirmVerified(localAssignmentStore, factoryChannel, factoryCampaignManager)
    }


    @Test
    internal fun `should fail to warm up the campaign`() = testCoroutineDispatcher.runTest {
        // given
        val directive =
            ScenarioWarmUpDirective("my-campaign", "my-scenario", "")
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns true
        coEvery {
            factoryCampaignManager.warmUpCampaignScenario(any(), any())
        } throws RuntimeException("A problem occurred")

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            localAssignmentStore.hasMinionsAssigned("my-scenario")
            factoryChannel.publishFeedback(
                ScenarioWarmUpFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            factoryCampaignManager.warmUpCampaignScenario("my-campaign", "my-scenario")
            factoryChannel.publishFeedback(
                ScenarioWarmUpFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }
        confirmVerified(localAssignmentStore, factoryChannel, factoryCampaignManager)
    }
}
