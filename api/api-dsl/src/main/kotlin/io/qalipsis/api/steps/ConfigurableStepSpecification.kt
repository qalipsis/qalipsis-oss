/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
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
