package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignName

/**
 * Interface for [io.qalipsis.api.orchestration.directives.Directive]s linked to a campaign.
 *
 * @author Eric Jessé
 */
interface CampaignManagementDirective {

    val campaignName: CampaignName
}