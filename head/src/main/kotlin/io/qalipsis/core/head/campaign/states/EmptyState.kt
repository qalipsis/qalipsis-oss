package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.configuration.AbortCampaignConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback

internal object EmptyState : CampaignExecutionState<CampaignExecutionContext> {

    override val isCompleted: Boolean = true

    override val campaignKey: CampaignKey = ""

    override fun inject(context: CampaignExecutionContext) = Unit

    override suspend fun init(): List<Directive> = emptyList()

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        throw IllegalStateException()
    }

    override suspend fun abort(abortConfiguration: AbortCampaignConfiguration): CampaignExecutionState<CampaignExecutionContext> {
        return this
    }
}