package io.qalipsis.api.scenario

import io.qalipsis.core.factories.orchestration.rampup.RampUpStrategy

/**
 * Interface of a specification supporting the configuration of a ramp-up to start minions.
 *
 * @author Eric Jessé
 */
interface RampUpSpecification {

    /**
     * Defines the ramp-up strategy to start all the minions on a scenario.
     */
    fun strategy(rampUpStrategy: RampUpStrategy)
}