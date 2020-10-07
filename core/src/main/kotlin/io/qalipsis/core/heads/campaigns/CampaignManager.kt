package io.qalipsis.core.heads.campaigns

import cool.graph.cuid.Cuid
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId


/**
 * Component to manage a new campaign.
 *
 * @author Eric Jessé
 *
 */
internal interface CampaignManager {

    /**
     * Start a new campaign for the provided scenarios.
     */
    suspend fun start(id: CampaignId = Cuid.createCuid(), scenarios: List<ScenarioId>,
                      onCriticalFailure: (String) -> Unit = { _ -> })
}
