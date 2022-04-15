package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Directive to trigger the assignment and creation of the minions for a scenario
 * in a new test campaign.
 */
@Serializable
@SerialName("ma")
data class MinionsAssignmentDirective(
    override val campaignName: CampaignName,
    val scenarioName: ScenarioName
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Definition of an instant when a given minion has to start.
 */
@Serializable
data class MinionStartDefinition(val minionId: MinionId, val timestamp: Long)

/**
 * Directive to start a set of minions at a certain point in time for a given scenario.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("ms")
data class MinionsStartDirective(
    override val campaignName: CampaignName,
    val scenarioName: ScenarioName,
    val startDefinitions: List<MinionStartDefinition>
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""

    override fun toString(): String {
        return "MinionsStartDirective(campaignName='$campaignName', scenarioName='$scenarioName', startDefinitionsCount=${startDefinitions.size})"
    }
}

/**
 * Directive to shutdown all the components of a minion.
 */
@Serializable
@SerialName("msd")
data class MinionsShutdownDirective(
    override val campaignName: CampaignName,
    val scenarioName: ScenarioName,
    val minionIds: List<MinionId>,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}
