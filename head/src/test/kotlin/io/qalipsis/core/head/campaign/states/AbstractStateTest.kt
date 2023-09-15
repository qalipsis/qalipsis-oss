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

package io.qalipsis.core.head.campaign.states

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.campaign.CampaignAutoStarter
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryWorkflowAssignmentResolver
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension


@WithMockk
internal abstract class AbstractStateTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    protected lateinit var campaignService: CampaignService

    @RelaxedMockK
    protected lateinit var factoryService: FactoryService

    @RelaxedMockK
    protected lateinit var campaign: RunningCampaign

    @RelaxedMockK
    protected lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @RelaxedMockK
    protected lateinit var headChannel: HeadChannel

    @RelaxedMockK
    protected lateinit var campaignAutoStarter: CampaignAutoStarter

    @RelaxedMockK
    protected lateinit var reportPublisher1: CampaignReportPublisher

    @RelaxedMockK
    protected lateinit var reportPublisher2: CampaignReportPublisher

    @RelaxedMockK
    protected lateinit var assignmentResolver: FactoryWorkflowAssignmentResolver

    protected val reportPublishers: Collection<CampaignReportPublisher> by lazy {
        listOf(reportPublisher1, reportPublisher2)
    }

    @RelaxedMockK
    protected lateinit var campaignHook1: CampaignHook

    @RelaxedMockK
    protected lateinit var campaignHook2: CampaignHook

    protected val campaignHooks: Collection<CampaignHook> by lazy {
        listOf(campaignHook1, campaignHook2)
    }

    @InjectMockKs
    protected lateinit var campaignExecutionContext: CampaignExecutionContext

    @BeforeEach
    internal fun setUp() {
        every { campaign.tenant } returns "my-tenant"
        every { campaign.key } returns "my-campaign"
        every { campaign.broadcastChannel } returns "my-broadcast-channel"
        every { campaign.feedbackChannel } returns "my-feedback-channel"
    }

}