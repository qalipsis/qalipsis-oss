package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Directive notifying a unique factory of the DAGs assigned to it in the context of a new campaign.
 *
 * @property campaignId the ID of the campaign
 * @property assignedDagsByScenario assignment of the scenarios and DAGs to the target factory
 */
@Serializable
@SerialName("fa")
data class FactoryAssignmentDirective(
    override val campaignId: CampaignId,
    val assignedDagsByScenario: Map<ScenarioId, Collection<DirectedAcyclicGraphId>>,
    val broadcastChannel: DispatcherChannel,
    val feedbackChannel: DispatcherChannel,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective


/**
 * Directive to warm-up the component of a scenario (steps...) when a new campaign starts.
 */
@Serializable
@SerialName("wup")
data class ScenarioWarmUpDirective(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective

/**
 * Directive to shutdown all the components of a scenario in a campaign.
 */
@Serializable
@SerialName("ssd")
data class CampaignScenarioShutdownDirective(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective

/**
 * Directive to shutdown all the components of a campaign.
 */
@Serializable
@SerialName("csd")
data class CampaignShutdownDirective(
    override val campaignId: CampaignId,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective

/**
 * Directive to notify components from the completion of a campaign.
 */
@Serializable
@SerialName("ccd")
data class CompleteCampaignDirective(
    val campaignId: CampaignId,
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
