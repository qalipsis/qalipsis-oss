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
