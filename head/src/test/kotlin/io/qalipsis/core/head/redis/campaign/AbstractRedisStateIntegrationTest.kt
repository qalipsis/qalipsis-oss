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

package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignAutoStarter
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryWorkflowAssignmentResolver
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Testcontainers

@ExperimentalLettuceCoroutinesApi
@WithMockk
@Testcontainers
@MicronautTest(environments = [ExecutionEnvironments.REDIS], startApplication = false)
internal abstract class AbstractRedisStateIntegrationTest : AbstractRedisIntegrationTest() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    protected lateinit var campaignService: CampaignService

    @RelaxedMockK
    protected lateinit var factoryService: FactoryService

    @RelaxedMockK
    protected lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @RelaxedMockK
    protected lateinit var headChannel: HeadChannel

    @RelaxedMockK
    protected lateinit var campaignAutoStarter: CampaignAutoStarter

    @InjectMockKs
    protected lateinit var campaignExecutionContext: CampaignExecutionContext

    @RelaxedMockK
    protected lateinit var assignmentResolver: FactoryWorkflowAssignmentResolver

    @RelaxedMockK
    protected lateinit var reportPublisher1: CampaignReportPublisher

    @RelaxedMockK
    protected lateinit var reportPublisher2: CampaignReportPublisher

    protected val reportPublishers: Collection<CampaignReportPublisher> by lazy {
        listOf(reportPublisher1, reportPublisher2)
    }

    protected lateinit var campaign: RunningCampaign

    @Inject
    protected lateinit var operations: CampaignRedisOperations

    @MockBean(FactoryService::class)
    fun factoryService(): FactoryService = factoryService

    @MockBean(HeadChannel::class)
    fun headChannel(): HeadChannel = headChannel

    @MockBean(CampaignAutoStarter::class)
    fun campaignAutoStarter(): CampaignAutoStarter = campaignAutoStarter

    @MockBean(CampaignExecutionContext::class)
    fun campaignExecutionContext(): CampaignExecutionContext = campaignExecutionContext

    @MockBean(FactoryWorkflowAssignmentResolver::class)
    fun assignmentResolver(): FactoryWorkflowAssignmentResolver = assignmentResolver

    @BeforeEach
    internal fun setUp() {
        campaign = spyk(
            RunningCampaign(
                tenant = "my-tenant", key = "my-campaign"
            ).also {
                it.broadcastChannel = "my-broadcast-channel"
                it.feedbackChannel = "my-feedback-channel"
                it.factories["node-1"] = FactoryConfiguration("node-1-channel")
                it.factories["node-2"] = FactoryConfiguration("node-2-channel")
        })
    }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        connection.sync().flushdb()
    }

    @AfterAll
    open fun tearDownAll() = Unit
}