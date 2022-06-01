package io.qalipsis.core.directives

import io.qalipsis.api.campaign.FactoryScenarioAssignment
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.configuration.AbortCampaignConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.validation.constraints.NotEmpty

/**
 * Directive notifying a unique factory of the DAGs assigned to it in the context of a new campaign.
 *
 * @property campaignKey the ID of the campaign
 */
@Serializable
@SerialName("fa")
data class FactoryAssignmentDirective(
    override val campaignKey: CampaignKey,
    @field:NotEmpty
    val assignments: Collection<FactoryScenarioAssignment>,
    val broadcastChannel: DispatcherChannel,
    val feedbackChannel: DispatcherChannel,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to warm-up the component of a scenario (steps...) when a new campaign starts.
 */
@Serializable
@SerialName("wup")
data class ScenarioWarmUpDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to shutdown all the components of a scenario in a campaign.
 */
@Serializable
@SerialName("ssd")
data class CampaignScenarioShutdownDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to shutdown all the components of a campaign.
 */
@Serializable
@SerialName("csd")
data class CampaignShutdownDirective(
    override val campaignKey: CampaignKey,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to notify components from the completion of a campaign.
 */
@Serializable
@SerialName("ccd")
data class CompleteCampaignDirective(
    val campaignKey: CampaignKey,
    val isSuccessful: Boolean = true,
    val message: String? = null,
    override val channel: DispatcherChannel
) : DescriptiveDirective()


/**
 * Directive to shutdown a factory.
 */
@Serializable
@SerialName("fsd")
data class FactoryShutdownDirective(
    override val channel: DispatcherChannel
) : DescriptiveDirective()


/**
 * Directive to abort the campaign for a given list of scenarios.
 */
@Serializable
@SerialName("cad")
data class CampaignAbortDirective(
    override val campaignKey: CampaignKey,
    override val channel: DispatcherChannel,
    /**
     * The list of scenario IDs for which the campaign has to be aborted.
     */
    val scenarioNames: List<ScenarioName>,
    /**
     * Configuration defining soft/hard aborting mode.
     */
    val abortCampaignConfiguration: AbortCampaignConfiguration = AbortCampaignConfiguration(hard = true)
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""

}
