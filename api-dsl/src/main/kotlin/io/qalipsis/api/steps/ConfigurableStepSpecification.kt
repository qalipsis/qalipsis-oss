/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.steps

import io.qalipsis.api.retry.RetryPolicy
import java.time.Duration

/**
 * Specification to configure a step specification.
 *
 * @param INPUT type of the data to process as input
 * @param OUTPUT type of the result forwarder to the output
 * @param SELF type of the step as visible to the scenario developer, it can be a concrete implementation or an interface, which will inherits from [ConfigurableStepSpecification].
 *
 * @author Eric Jessé
 */
interface ConfigurableStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF> {

    /**
     * Configures the step with type-specific settings.
     */
    fun configure(specification: SELF.() -> Unit): StepSpecification<INPUT, OUTPUT, *> {
        val nameBeforeConfiguration = name
        @Suppress("UNCHECKED_CAST")
        (this as SELF).specification()

        // If the name was changed, the step has to be declared again in the scenario with its new name.
        if (name != nameBeforeConfiguration) {
            scenario.register(this)
        }
        return this
    }

    /**
     * Defines the timeout of the step execution on a single context, in milliseconds.
     */
    fun timeout(duration: Long) = timeout(Duration.ofMillis(duration))

    /**
     * Defines the timeout of the step execution on a single context.
     */
    fun timeout(duration: Duration)

    /**
     * Defines the individual retry strategy on the step. When none is set, the default one of the scenario is used.
     */
    fun retry(retryPolicy: RetryPolicy)

    /**
     * Defines how many times and how often the step execution has to be repeated.
     *
     * @param iterations count of iterations to perform
     * @param period delay to wait between the end of an execution and the start of the next one (default to no delay)
     * @param stopOnError stop the iterations when an execution fails (default to false)
     */
    fun iterate(iterations: Long, period: Duration = Duration.ZERO, stopOnError: Boolean = false)

    /**
     * Configures the reporting for the step.
     */
    fun report(specification: StepReportingSpecification.() -> Unit)
}
