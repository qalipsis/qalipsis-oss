package io.qalipsis.api.scenario

import io.qalipsis.api.retry.RetryPolicy

/**
 * Interface of a specification supporting the configuration of a retry policy.
 *
 * @author Eric Jessé
 */
interface RetrySpecification {

    /**
     * Defines the default retry strategy for all the steps of the scenario.
     * The strategy can be redefined individually for each step.
     */
    fun retryPolicy(retryPolicy: RetryPolicy)
}