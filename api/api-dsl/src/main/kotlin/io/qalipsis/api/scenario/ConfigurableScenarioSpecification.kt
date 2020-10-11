package io.qalipsis.api.scenario

/**
 * Interface of a scenario to be configured.
 *
 * @author Eric Jessé
 */
interface ConfigurableScenarioSpecification : RetrySpecification {

    /**
     * Defines how the start of the minion should evolve in the scenario.
     */
    fun rampUp(specification: RampUpSpecification.() -> Unit)

    /**
     * Default number of minions. This value is multiplied by a runtime factor to provide the total number of minions on the scenario.
     */
    var minionsCount: Int
}