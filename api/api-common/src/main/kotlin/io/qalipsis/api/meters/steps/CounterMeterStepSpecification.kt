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