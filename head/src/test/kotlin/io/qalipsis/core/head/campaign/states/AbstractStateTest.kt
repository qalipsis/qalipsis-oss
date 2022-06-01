package io.qalipsis.core.head.campaign.states

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.head.campaign.CampaignAutoStarter
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
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
    protected lateinit var factoryService: FactoryService

    @RelaxedMockK
    protected lateinit var campaign: CampaignConfiguration

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

    protected val reportPublishers: Collection<CampaignReportPublisher> by lazy {
        listOf(reportPublisher1, reportPublisher2)
    }

    @InjectMockKs
    protected lateinit var campaignExecutionContext: CampaignExecutionContext

    @BeforeEach
    internal fun setUp() {
        every { campaign.key } returns "my-campaign"
        every { campaign.broadcastChannel } returns "my-broadcast-channel"
        every { campaign.feedbackChannel } returns "my-feedback-channel"
    }

}