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
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.steps.expectations.ThroughputExpectationSpec
import io.qalipsis.api.meters.steps.expectations.impl.ComparableValueMeterExpectationSpecification
import io.qalipsis.api.meters.steps.expectations.impl.ThroughputExpectationSpecImpl
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification

/**
 * Specification for a [io.qalipsis.core.factory.steps.meter.ThroughputMeterStep] to allow for configuration of failure conditions.
 *
 * @author Francisca Eze
 */
interface ThroughputMeterStepSpecification<INPUT> :
    StepSpecification<INPUT, INPUT, ThroughputMeterStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, INPUT, ThroughputMeterStepSpecification<INPUT>> {

    /**
     * Allows specification of failure conditions on the throughput meter.
     */
    fun shouldSatisfy(block: ThroughputExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>

}

/**
 * Specification for a [ThroughputMeterStepSpecification].
 *
 * @author Francisca Eze
 */
@Spec
data class ThroughputMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : ThroughputMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, ThroughputMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueMeterExpectationSpecification<Throughput, Double>> = mutableListOf()

    val percentiles: MutableSet<Double> = mutableSetOf()

    override fun shouldSatisfy(block: ThroughputExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val throughputFailureConditionSpec = ThroughputExpectationSpecImpl(percentiles)
        throughputFailureConditionSpec.block()
        checks = throughputFailureConditionSpec.checks
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
fun <INPUT> StepSpecification<*, INPUT, *>.throughput(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double = { _, _ -> 1.0 }
): ThroughputMeterStepSpecification<INPUT> {
    val step = ThroughputMeterStepSpecificationImpl(name, block)
    step.name = name
    this.add(step)
    return step
}