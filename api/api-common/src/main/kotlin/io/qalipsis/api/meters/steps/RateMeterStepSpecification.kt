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
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.steps.expectations.RateExpectationSpec
import io.qalipsis.api.meters.steps.expectations.impl.ComparableValueMeterExpectationSpecification
import io.qalipsis.api.meters.steps.expectations.impl.RateExpectationSpecImpl
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification

/**
 * Specification for a [io.qalipsis.core.factory.steps.meter.RateMeterStep] to allow for configuration of failure conditions.
 *
 * @author Francisca Eze
 */
interface RateMeterStepSpecification<INPUT> : StepSpecification<INPUT, INPUT, RateMeterStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, INPUT, RateMeterStepSpecification<INPUT>> {
    /**
     * Allows specification of failure conditions on the rate meter.
     */
    fun shouldSatisfy(block: RateExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Specification for a [RateMeterStepSpecification].
 *
 * @author Francisca Eze
 */
@Spec
data class RateMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> RateIncrement,
) : RateMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, RateMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueMeterExpectationSpecification<Rate, Double>> = mutableListOf()

    override fun shouldSatisfy(block: RateExpectationSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val rateFailureConditionSpec = RateExpectationSpecImpl()
        rateFailureConditionSpec.block()
        checks = rateFailureConditionSpec.checks
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
fun <INPUT> StepSpecification<*, INPUT, *>.rate(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> RateIncrement
): RateMeterStepSpecification<INPUT> {
    val step = RateMeterStepSpecificationImpl(name, block)
    step.name = name
    this.add(step)
    return step
}

/**
 * Represents incremental updates applied to a Rate meter.
 * Each execution contributes deltas to the observedDelta and totalDelta counters.
 * Delta represents the amount by which an internal counter is increased or decreased as a result of a single step execution.
 *
 * @property observedDelta the measured or observed quantity of interest
 * @property totalDelta the reference or cumulative quantity that provides context for the observedDelta value
 *
 * @author Francisca Eze
 */
data class RateIncrement(val observedDelta: Double, val totalDelta: Double)