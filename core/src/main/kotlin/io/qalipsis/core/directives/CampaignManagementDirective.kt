package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignKey

/**
 * Interface for [io.qalipsis.api.orchestration.directives.Directive]s linked to a campaign.
 *
 * @author Eric Jessé
 */
interface CampaignManagementDirective {
    val campaignKey: CampaignKey
    var tenant: String
}