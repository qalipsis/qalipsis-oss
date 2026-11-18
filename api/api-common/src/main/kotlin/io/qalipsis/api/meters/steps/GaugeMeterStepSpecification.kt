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
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.steps.expectations.GaugeExpectationSpec
import io.qalipsis.api.meters.steps.expectations.impl.ComparableValueMeterExpectationSpecification
import io.qalipsis.api.meters.steps.expectations.impl.GaugeExpectationSpecImpl
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification

/**
 * Operation applied to the gauge with the value returned by the block of a gauge step.
 *
 * @author Eric Jessé
 */
enum class GaugeOperation {

    /**
     * Replaces the value of the gauge: the block returns a level, such as a queue depth or a consumer lag.
     */
    SET,

    /**
     * Adds the value to the gauge: the block returns a contribution to the value tracked by the gauge.
     */
    ADD

}

/**
 * Specification for a [io.qalipsis.core.factory.steps.meter.GaugeMeterStep] to allow for configuration of failure conditions.
 *
 * @author Francisca Eze
 */
interface GaugeMeterStepSpecification<INPUT> : StepSpecification<INPUT, INPUT, GaugeMeterStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, INPUT, GaugeMeterStepSpecification<INPUT>> {

    /**
     * Allows specification of failure conditions on the gauge meter.
     */
    fun shouldSatisfy(block: GaugeExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Implementation of [GaugeMeterStepSpecification].
 *
 * @author Francisca Eze
 */
@Spec
data class GaugeMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val operation: GaugeOperation,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : GaugeMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, GaugeMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueMeterExpectationSpecification<Gauge, Double>> = mutableListOf()

    override fun shouldSatisfy(block: GaugeExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val gaugeFailureConditionSpecImpl = GaugeExpectationSpecImpl()
        gaugeFailureConditionSpecImpl.block()
        checks = gaugeFailureConditionSpecImpl.checks
        return this
    }

}

/**
 * Executes the block on the context to extract a value to be recorded in the meter.
 *
 * By default the value is a level and not a contribution: the gauge holds the latest value returned by [block],
 * which is the one evaluated by [GaugeMeterStepSpecification.shouldSatisfy] and published to the meter
 * backends. Pass [GaugeOperation.ADD] to add the values to each other instead, when the block returns a
 * variation of the value tracked by the gauge.
 *
 * @param name the name of the meter.
 * @param operation whether the value returned by [block] replaces the value of the gauge or is added to it.
 * @param block the rule to convert the input and the context into the output.
 *
 * @author Francisca Eze
 */
fun <INPUT> StepSpecification<*, INPUT, *>.gauge(
    name: String,
    operation: GaugeOperation = GaugeOperation.SET,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): GaugeMeterStepSpecification<INPUT> {
    val step = GaugeMeterStepSpecificationImpl(name, operation, block)
    step.name = name
    this.add(step)
    return step
}
