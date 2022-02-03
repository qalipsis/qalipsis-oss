package io.qalipsis.core.head.campaign.states

import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.head.campaign.CampaignConfiguration

internal open class DisabledState(
    protected val campaign: CampaignConfiguration,
    private val isSuccessful: Boolean = true
) : AbstractCampaignExecutionState(campaign.id) {

    override val isCompleted: Boolean = true

    override suspend fun doInit(): List<Directive> {
        return listOf(
            CompleteCampaignDirective(
                campaignId,
                isSuccessful = isSuccessful,
                message = campaign.message,
                idGenerator.short(),
                campaign.broadcastChannel
            )
        )
    }

    override fun toString(): String {
        return "DisabledState(campaign=$campaign, isSuccessful=$isSuccessful, isCompleted=$isCompleted)"
    }

}