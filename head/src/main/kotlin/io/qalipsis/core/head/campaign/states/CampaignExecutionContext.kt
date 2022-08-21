package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.head.campaign.CampaignAutoStarter
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import jakarta.inject.Singleton
import javax.annotation.Nullable

/**
 * Context containing the required components for the execution of a campaign state.
 *
 * @author Eric Jessé
 */
@Singleton
internal class CampaignExecutionContext(
    val campaignService: CampaignService,
    val factoryService: FactoryService,
    val campaignReportStateKeeper: CampaignReportStateKeeper,
    val headChannel: HeadChannel,
    val reportPublishers: Collection<CampaignReportPublisher>,
    @Nullable val campaignAutoStarter: CampaignAutoStarter?
)