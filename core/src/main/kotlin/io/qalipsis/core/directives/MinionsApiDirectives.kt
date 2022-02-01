package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Directive to trigger the assignment and creation of the minions for a scenario
 * in a new test campaign.
 */
@Serializable
@SerialName("ma")
data class MinionsAssignmentDirective(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val key: DirectiveKey,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective

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
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    val startDefinitions: List<MinionStartDefinition>,
    override val key: DirectiveKey,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective

/**
 * Directive to shutdown all the components of a minion.
 */
@Serializable
@SerialName("msd")
data class MinionsShutdownDirective(
    override val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    val minionIds: List<MinionId>,
    override val key: DirectiveKey,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective
