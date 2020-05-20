package io.evolue.core.cross.driving.directives

import io.evolue.api.context.ScenarioId

/**
 * Directives processed by the factories on behalf of the head.
 */

/**
 * Directive to prepare the creation of the minions for a given scenario. This directive can only be read once.
 * Hence, a [QueueDirective] with a single value is used, to remove the directive after the first read.
 *
 * It is published to the directive consumers as a [MinionsCreationPreparationDirectiveReference].
 */
internal class MinionsCreationPreparationDirective(
    /**
     * The ID of the scenario for which the minion has to be created.
     */
    val scenarioId: ScenarioId,
    /**
     * Number of minions to create.
     */
    count: Int

) : SingleUseDirective<Int, SingleUseDirectiveReference<Int>>(count) {

    override fun toReference(): SingleUseDirectiveReference<Int> {
        return MinionsCreationPreparationDirectiveReference(key, scenarioId)
    }
}

/**
 * Transportable representation of a [MinionsCreationPreparationDirective].
 */
internal class MinionsCreationPreparationDirectiveReference(
    key: DirectiveKey,
    /**
     * The ID of the scenario for which the minion has to be created.
     */
    val scenarioId: ScenarioId
) : SingleUseDirectiveReference<Int>(key)

/**
 * Directive to calculate the ramp-up of the minions given the scenario strategy.
 */
internal class MinionsRampUpPreparationDirective(
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
    val speedFactor: Double = 1.0

) : DescriptiveDirective()