package io.qalipsis.api.scenario

import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.retry.RetryPolicy

/**
 * Interface for an implementation of [ScenarioSpecification], on which the configuration can be read.
 *
 * @author Eric Jessé
 */
interface ConfiguredScenarioSpecification : StepSpecificationRegistry {

    /**
     * Default minions count to run in the tree under load when runtime factor is 1.
     */
    val minionsCount: Int

    /**
     * [RampUpStrategy] defining how the start of the minion should evolve in the scenario.
     */
    val rampUpStrategy: RampUpStrategy?

    /**
     * Default [RetryPolicy] defined for all the steps of the scenario, when not otherwise specified.
     */
    val retryPolicy: RetryPolicy?

    val dagsCount: Int
}
