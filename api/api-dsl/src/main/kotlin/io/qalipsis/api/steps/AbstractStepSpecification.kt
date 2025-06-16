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

import io.qalipsis.api.constraints.PositiveOrZeroDuration
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.StepSpecificationRegistry
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 * Generic specification for the step specifications. The actual operations are added by extensions.
 *
 * @param INPUT type of the data to process as input
 * @param OUTPUT type of the result forwarder to the output
 * @param SELF type of the step as visible to the scenario developer, it can be a concrete implementation or an interface, which will inherits from [ConfigurableStepSpecification].
 *
 * @author Eric Jessé
 */
abstract class AbstractStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    ConfigurableStepSpecification<INPUT, OUTPUT, SELF> {

    override var name: StepName = ""

    @field:NotNull
    override lateinit var scenario: StepSpecificationRegistry

    @field:NotBlank
    override var directedAcyclicGraphName: DirectedAcyclicGraphName = ""

    override var timeout: Duration? = null

    @field:Positive
    override var iterations: Long = 1

    @field:PositiveOrZeroDuration
    override var iterationPeriods: Duration = Duration.ZERO

    override var stopIterationsOnError: Boolean = false

    override var retryPolicy: RetryPolicy? = null

    override val nextSteps = mutableListOf<StepSpecification<*, *, *>>()

    override var reporting = StepReportingSpecification()

    private var tagsSet = false

    override var tags: Map<String, String> = emptyMap()

    override fun timeout(duration: Duration) {
        timeout = duration
    }

    override fun retry(retryPolicy: RetryPolicy) {
        this.retryPolicy = retryPolicy
    }

    override fun iterate(iterations: Long, period: Duration, stopOnError: Boolean) {
        this.iterations = iterations
        this.iterationPeriods = period
        this.stopIterationsOnError = stopOnError
    }

    override fun add(step: StepSpecification<*, *, *>) {
        // If no selector is specified on the next step, they are inherited.
        if (step.tags.isEmpty() || (step as? AbstractStepSpecification<*, *, *>)?.tagsSet != true) {
            step.tag(tags)
        }
        nextSteps.add(step)
        scenario.registerNext(this, step)
    }

    override fun report(specification: StepReportingSpecification.() -> Unit) {
        reporting.specification()
    }

    override fun tag(tags: Map<String, String>) {
        tagsSet = true
        this.tags = tags
    }
}
