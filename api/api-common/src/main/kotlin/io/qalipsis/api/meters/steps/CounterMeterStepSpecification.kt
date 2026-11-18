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
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.steps.expectations.CounterExpectationSpec
import io.qalipsis.api.meters.steps.expectations.impl.ComparableValueMeterExpectationSpecification
import io.qalipsis.api.meters.steps.expectations.impl.CounterExpectationSpecImpl
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification

/**
 * Specification for a [io.qalipsis.core.factory.steps.meter.CounterMeterStep] to allow for configuration of failure conditions.
 *
 * @author Francisca Eze
 */
interface CounterMeterStepSpecification<INPUT> :
    StepSpecification<INPUT, INPUT, CounterMeterStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, INPUT, CounterMeterStepSpecification<INPUT>> {

    /**
     * Allows specification of failure conditions on the count meter.
     */
    fun shouldSatisfy(block: CounterExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Implementation of [CounterMeterStepSpecification].
 */
@Spec
data class CounterMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : CounterMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, CounterMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueMeterExpectationSpecification<Counter, Double>> = mutableListOf()

    override fun shouldSatisfy(block: CounterExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val counterFailureConditionSpecImpl = CounterExpectationSpecImpl()
        counterFailureConditionSpecImpl.block()
        checks = counterFailureConditionSpecImpl.checks
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
fun <INPUT> StepSpecification<*, INPUT, *>.counter(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): CounterMeterStepSpecification<INPUT> {
    val step = CounterMeterStepSpecificationImpl(name, block)
    step.name = name
    this.add(step)
    return step
}