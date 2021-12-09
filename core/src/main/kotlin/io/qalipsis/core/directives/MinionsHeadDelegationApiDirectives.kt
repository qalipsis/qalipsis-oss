package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.directives.DescriptiveDirective
import io.qalipsis.api.orchestration.directives.DirectiveKey
import io.qalipsis.api.orchestration.directives.DispatcherChannel
import io.qalipsis.api.orchestration.directives.SingleUseDirective
import io.qalipsis.api.orchestration.directives.SingleUseDirectiveReference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Directives processed by the factories on behalf of the head.
 */

/**
 * Directive to prepare the creation of the minions for a given scenario. This directive can only be read once.
 * Hence, a [QueueDirective] with a single value is used, to remove the directive after the first read.
 *
 * It is published to the directive consumers as a [MinionsCreationPreparationDirectiveReference].
 */
@Serializable
@SerialName("mcpd")
class MinionsCreationPreparationDirective(
    /**
     * The ID of the campaign for which the minion has to be created.
     */
    val campaignId: CampaignId,
    /**
     * The ID of the scenario for which the minion has to be created.
     */
    val scenarioId: ScenarioId,

    override val value: Int,
    override val key: DirectiveKey,

    /**
     * Directive channel.
     */
    override val channel: DispatcherChannel

) : SingleUseDirective<Int, SingleUseDirectiveReference<Int>>() {

    override fun toReference(): SingleUseDirectiveReference<Int> {
        return MinionsCreationPreparationDirectiveReference(key, campaignId, scenarioId, channel)
    }
}

/**
 * Transportable representation of a [MinionsCreationPreparationDirective].
 */
@Serializable
@SerialName("mcpdRef")
class MinionsCreationPreparationDirectiveReference(

    override val key: DirectiveKey,

    /**
     * The ID of the campaign for which the minion has to be created.
     */
    val campaignId: CampaignId,
    /**
     * The ID of the scenario for which the minion has to be created.
     */
    val scenarioId: ScenarioId,

    /**
     * Directive channel.
     */
    override val channel: DispatcherChannel
) : SingleUseDirectiveReference<Int>()

/**
 * Directive to calculate the ramp-up of the minions given the scenario strategy.
 */
@Serializable
@SerialName("mrpd")
class MinionsRampUpPreparationDirective(
    /**
     * The ID of the campaign for which the minion has to be created.
     */
    val campaignId: CampaignId,

    /**
     * The ID of the scenario for which the ramp-up has to be executed.
     */
    val scenarioId: ScenarioId,

    /**
     * The time to wait before the first minion is executed.
     * This should take the latency of the factories into consideration.
     */
    val startOffsetMs: Long = 3000,

    /**
     * The speed factor to apply on the ramp-up strategy. Each strategy will apply it differently depending on
     * its own implementation.
     */
    val speedFactor: Double = 1.0,

    override val key: DirectiveKey,

    /**
     * Directive channel.
     */
    override val channel: DispatcherChannel

) : DescriptiveDirective()
