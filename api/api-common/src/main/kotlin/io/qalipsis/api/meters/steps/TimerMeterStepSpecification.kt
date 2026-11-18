/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.meters.steps

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.meters.steps.expectations.TimerExpectationSpec
import io.qalipsis.api.meters.steps.expectations.impl.ComparableValueMeterExpectationSpecification
import io.qalipsis.api.meters.steps.expectations.impl.TimerExpectationSpecImpl
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification
import java.time.Duration

/**
 * Specification for a [io.qalipsis.core.factory.steps.meter.TimerMeterStep] to allow for configuration of failure conditions.
 *
 * @author Francisca Eze
 */
interface TimerMeterStepSpecification<INPUT> :
    StepSpecification<INPUT, INPUT, TimerMeterStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, INPUT, TimerMeterStepSpecification<INPUT>> {

    /**
     * Allows specification of failure conditions on the timer meter.
     */
    fun shouldSatisfy(block: TimerExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Specification for a [TimerMeterStepSpecification].
 *
 * @author Francisca Eze
 */
@Spec
data class TimerMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Duration,
) : TimerMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, TimerMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueMeterExpectationSpecification<Timer, Duration>> = mutableListOf()

    val percentiles: MutableSet<Double> = mutableSetOf()

    override fun shouldSatisfy(block: TimerExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val timerFailureConditionSpecImpl = TimerExpectationSpecImpl(percentiles)
        timerFailureConditionSpecImpl.block()
        checks = timerFailureConditionSpecImpl.checks
        return this
    }

}

/**
 * Executes the block on the context to extract a value to be recorded in the meter.
 *
 * @param name the name of the meter.
 * @param block the rule to convert the input and the context into the output.
 *
 * @author Francisca Eze
 */
fun <INPUT> StepSpecification<*, INPUT, *>.timer(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Duration
): TimerMeterStepSpecification<INPUT> {
    val step = TimerMeterStepSpecificationImpl(name, block)
    step.name = name
    this.add(step)
    return step
}