package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.directives.DescriptiveDirective
import io.qalipsis.api.orchestration.directives.DirectiveKey
import io.qalipsis.api.orchestration.directives.DispatcherChannel
import io.qalipsis.api.orchestration.directives.ListDirective
import io.qalipsis.api.orchestration.directives.ListDirectiveReference
import io.qalipsis.api.orchestration.directives.QueueDirective
import io.qalipsis.api.orchestration.directives.QueueDirectiveReference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * Directive to create minions for a given scenario and directed acyclic graph.
 *
 * It is published to the directive consumers as a [MinionsCreationDirectiveReference].
 */
@Serializable
@SerialName("mcd")
class MinionsCreationDirective(
    /**
     * The ID of the campaign for which the minion has to be created.
     */
    val campaignId: CampaignId,
    /**
     * The ID of the scenario for which the minion has to be created.
     */
    val scenarioId: ScenarioId,
    /**
     * The ID of the scenario for which the minion has to be created.
     */
    val dagId: DirectedAcyclicGraphId,
    /**
     * Values to initialize the queue.
     */
    override val values: List<MinionId>,

    override val key: DirectiveKey,

    /**
     * Directive channel.
     */
    override val channel: DispatcherChannel
) : QueueDirective<MinionId, QueueDirectiveReference<MinionId>>() {

    override fun toReference(): QueueDirectiveReference<MinionId> {
        return MinionsCreationDirectiveReference(key, campaignId, scenarioId, dagId, channel)
    }
}

/**
 * Transportable representation of a [MinionsCreationDirective].
 */
@Serializable
@SerialName("mcdRef")
class MinionsCreationDirectiveReference(
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
     * The ID of the scenario for which the minion has to be created.
     */
    val dagId: DirectedAcyclicGraphId,

    /**
     * Directive channel.
     */
    override val channel: DispatcherChannel
) : QueueDirectiveReference<MinionId>()

/**
 * Definition of an instant when a given minion has to start.
 */
@Serializable
data class MinionStartDefinition(val minionId: MinionId, val timestamp: Long)

/**
 * Directive to start minions for a given scenario.
 *
 * It is published to the directive consumers as a [MinionsStartDirectiveReference].
 */
@Serializable
@SerialName("msd")
class MinionsStartDirective(
    /**
     * The ID of the scenario for which the minion has to be started.
     * This field is used for filtering in the factory.
     */
    val scenarioId: ScenarioId,
    /**
     * Values to initialize the queue.
     */
    override val values: List<MinionStartDefinition>,

    override val key: DirectiveKey,

    /**
     * Directive channel.
     */
    override val channel: DispatcherChannel
) : ListDirective<MinionStartDefinition, ListDirectiveReference<MinionStartDefinition>>() {

    override fun toReference(): ListDirectiveReference<MinionStartDefinition> {
        return MinionsStartDirectiveReference(key, scenarioId, channel)
    }
}

/**
 * Transportable representation of a [MinionsStartDirective].
 */
@Serializable
@SerialName("msdRef")
class MinionsStartDirectiveReference(
    override val key: DirectiveKey,
    /**
     * The ID of the scenario for which the minion has to be started.
     * This field is used for filtering in the factory.
     */
    val scenarioId: ScenarioId,

    /**
     * Directive channel.
     */
    override val channel: DispatcherChannel
) : ListDirectiveReference<MinionStartDefinition>()

/**
 * Directive to start a new campaign for a given scenario.
 */
@Serializable
@SerialName("csd")
class CampaignStartDirective(
    /**
     * The ID of the campaign to start.
     */
    val campaignId: CampaignId,
    /**
     * The ID of the scenario for which the campaign has to be started.
     */
    val scenarioId: ScenarioId,
    override val key: DirectiveKey,

    /**
     * Directive channel.
     */
    override val channel: DispatcherChannel
) : DescriptiveDirective()
