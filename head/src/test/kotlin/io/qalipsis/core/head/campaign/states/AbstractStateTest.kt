package io.qalipsis.core.head.campaign.states

import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.head.campaign.CampaignConfiguration
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
    protected lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    protected lateinit var factoryService: FactoryService

    @RelaxedMockK
    protected lateinit var campaign: CampaignConfiguration

    @RelaxedMockK
    protected lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @BeforeEach
    internal fun setUp() {
        every { campaign.id } returns "my-campaign"
        every { campaign.broadcastChannel } returns "my-broadcast-channel"

        every { idGenerator.short() } returnsMany (1..10).map { "the-directive-$it" }
    }

}