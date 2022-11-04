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
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CampaignShutdownDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var campaignLifeCycleAware1: CampaignLifeCycleAware

    @RelaxedMockK
    private lateinit var campaignLifeCycleAware2: CampaignLifeCycleAware

    private val campaignLifeCycleAwares: Collection<CampaignLifeCycleAware> by lazy {
        listOf(campaignLifeCycleAware1, campaignLifeCycleAware2)
    }

    @InjectMockKs
    private lateinit var processor: CampaignShutdownDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept campaign shutdown directive`() {
        val directive = CampaignShutdownDirective("my-campaign", "")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign") } returns true

        Assertions.assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept campaign shutdown directive when the campaign is not executed locally`() {
        val directive = CampaignShutdownDirective("my-campaign", "")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept not campaign shutdown directive`() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    fun `should process the directive and confirm when all is right`() = testCoroutineDispatcher.runTest {
        val directive = CampaignShutdownDirective("my-campaign", "")
        val campaign = relaxedMockk<Campaign>()
        every { factoryCampaignManager.runningCampaign } returns campaign

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                CampaignShutdownFeedback(campaignKey = "my-campaign", status = FeedbackStatus.IN_PROGRESS)
            )
            factoryCampaignManager.runningCampaign
            campaignLifeCycleAware1.close(refEq(campaign))
            campaignLifeCycleAware2.close(refEq(campaign))
            factoryChannel.publishFeedback(
                CampaignShutdownFeedback(campaignKey = "my-campaign", status = FeedbackStatus.COMPLETED)
            )
        }
        confirmVerified(factoryCampaignManager, factoryChannel, campaignLifeCycleAware1, campaignLifeCycleAware2)
    }

    @Test
    fun `should process the directive and fails when there is an exception`() =
        testCoroutineDispatcher.runTest {
            val directive = CampaignShutdownDirective("my-campaign", "")
            val campaign = relaxedMockk<Campaign>()
            every { factoryCampaignManager.runningCampaign } returns campaign
            coEvery { campaignLifeCycleAware1.close(refEq(campaign)) } throws RuntimeException("A problem occurred")

            // when
            processor.notify(directive)

            // then
            coVerifyOrder {
                factoryChannel.publishFeedback(
                    CampaignShutdownFeedback(campaignKey = "my-campaign", status = FeedbackStatus.IN_PROGRESS)
                )
                factoryCampaignManager.runningCampaign
                campaignLifeCycleAware1.close(refEq(campaign))
                factoryChannel.publishFeedback(
                    CampaignShutdownFeedback(
                        campaignKey = "my-campaign",
                        status = FeedbackStatus.FAILED,
                        errorMessage = "A problem occurred"
                    )
                )
            }
            confirmVerified(factoryCampaignManager, factoryChannel, campaignLifeCycleAware1, campaignLifeCycleAware2)
        }
}