package io.evolue.core.cross.driving.directives

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.MinionId
import io.evolue.api.context.ScenarioId


/**
 * Directive to create minions for a given scenario and directed acyclic graph.
 *
 * It is published to the directive consumers as a [MinionsCreationDirectiveReference].
 */
internal class MinionsCreationDirective(
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
    values: List<MinionId>
) : QueueDirective<MinionId, QueueDirectiveReference<MinionId>>(values) {

    override fun toReference(): QueueDirectiveReference<MinionId> {
        return MinionsCreationDirectiveReference(key, scenarioId, dagId)
    }
}

/**
 * Transportable representation of a [MinionsCreationDirective].
 */
internal class MinionsCreationDirectiveReference(
    key: DirectiveKey,
    /**
     * The ID of the scenario for which the minion has to be created.
     */
    val scenarioId: ScenarioId,
    /**
     * The ID of the scenario for which the minion has to be created.
     */
    val dagId: DirectedAcyclicGraphId
) : QueueDirectiveReference<MinionId>(key)

/**
 * Definition of an instant when a given minion has to start.
 */
internal data class MinionStartDefinition(val minionId: MinionId, val timestamp: Long)

/**
 * Directive to start minions for a given scenario.
 *
 * It is published to the directive consumers as a [MinionsStartDirectiveReference].
 */
internal class MinionsStartDirective(
    /**
     * The ID of the scenario for which the minion has to be started.
     * This field is used for filtering in the factory.
     */
    val scenarioId: ScenarioId,
    /**
     * Values to initialize the queue.
     */
    values: List<MinionStartDefinition>
) : ListDirective<MinionStartDefinition, ListDirectiveReference<MinionStartDefinition>>(values) {

    override fun toReference(): ListDirectiveReference<MinionStartDefinition> {
        return MinionsStartDirectiveReference(key, scenarioId)
    }
}

/**
 * Transportable representation of a [MinionsStartDirective].
 */
internal class MinionsStartDirectiveReference(
    key: DirectiveKey,
    /**
     * The ID of the scenario for which the minion has to be started.
     * This field is used for filtering in the factory.
     */
    val scenarioId: ScenarioId
) : ListDirectiveReference<MinionStartDefinition>(key)

/**
 * Directive to start the singleton minions for a given scenario.
 */
internal class MinionsStartSingletonsDirective(
    /**
     * The ID of the scenario for which the singleton minions has to be started.
     */
    val scenarioId: ScenarioId
) : DescriptiveDirective()