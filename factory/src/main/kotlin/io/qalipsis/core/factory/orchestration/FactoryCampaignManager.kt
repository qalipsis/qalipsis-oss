package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.rampup.RampUpConfiguration

internal interface FactoryCampaignManager {

    val feedbackNodeId: String

    /**
     * Initializes the state for the start of a new campaign.
     */
    suspend fun initCampaign(campaignId: CampaignId, scenariosIds: Collection<ScenarioId>)

    /**
     * Verifies whether the campaign is locally executed.
     */
    fun isLocallyExecuted(campaignId: CampaignId): Boolean

    /**
     * Verifies whether the scenario is locally executed.
     */
    fun isLocallyExecuted(campaignId: CampaignId, scenarioId: ScenarioId): Boolean

    /**
     * Starts all the steps for a campaign and the related singleton minions.
     */
    suspend fun warmUpCampaignScenario(campaignId: CampaignId, scenarioId: ScenarioId)

    /**
     * Calculates the ramping of all the minions under load for the given scenario.
     */
    suspend fun prepareMinionsRampUp(
        campaignId: CampaignId,
        scenario: Scenario,
        rampUpConfiguration: RampUpConfiguration
    ): List<MinionStartDefinition>

    suspend fun notifyCompleteMinion(
        minionId: MinionId,
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        dagId: DirectedAcyclicGraphId
    )

    /**
     * Shutdown the related minions of the specified campaign.
     */
    suspend fun shutdownMinions(campaignId: CampaignId, minionIds: Collection<MinionId>)

    /**
     * Stops all the components of a scenario in a campaign.
     */
    suspend fun shutdownScenario(campaignId: CampaignId, scenarioId: ScenarioId)

    /**
     * Stops all the steps for a campaign and the related singleton minions.
     */
    suspend fun shutdownCampaign(campaignId: CampaignId)
}