package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.rampup.RampUpConfiguration

internal interface FactoryCampaignManager : CampaignLifeCycleAware {

    val runningCampaign: Campaign

    /**
     * Verifies whether the campaign is locally executed.
     */
    fun isLocallyExecuted(campaignKey: CampaignKey): Boolean

    /**
     * Verifies whether the scenario is locally executed.
     */
    fun isLocallyExecuted(campaignKey: CampaignKey, scenarioName: ScenarioName): Boolean

    /**
     * Starts all the steps for a campaign and the related singleton minions.
     */
    suspend fun warmUpCampaignScenario(campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Calculates the ramping of all the minions under load for the given scenario.
     */
    suspend fun prepareMinionsRampUp(
        campaignKey: CampaignKey,
        scenario: Scenario,
        rampUpConfiguration: RampUpConfiguration
    ): List<MinionStartDefinition>

    suspend fun notifyCompleteMinion(
        minionId: MinionId,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagId: DirectedAcyclicGraphName
    )

    /**
     * Shutdown the related minions of the specified campaign.
     */
    suspend fun shutdownMinions(campaignKey: CampaignKey, minionIds: Collection<MinionId>)

    /**
     * Stops all the components of a scenario in a campaign.
     */
    suspend fun shutdownScenario(campaignKey: CampaignKey, scenarioName: ScenarioName)

}