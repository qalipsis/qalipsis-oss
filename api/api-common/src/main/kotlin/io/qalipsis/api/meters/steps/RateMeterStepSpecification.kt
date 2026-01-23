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