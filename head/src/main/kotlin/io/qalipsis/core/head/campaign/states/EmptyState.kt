package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper

internal object EmptyState : CampaignExecutionState {

    override val isCompleted: Boolean = true

    override val campaignId: CampaignId = ""

    override suspend fun init(
        factoryService: FactoryService,
        campaignReportStateKeeper: CampaignReportStateKeeper,
        idGenerator: IdGenerator
    ): List<Directive> = emptyList()

    override suspend fun process(directive: Directive): CampaignExecutionState {
        throw IllegalStateException()
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState {
        throw IllegalStateException()
    }
}