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
import io.qalipsis.api.meters.steps.failure.RateFailureConditionSpec
import io.qalipsis.api.meters.steps.failure.impl.ComparableValueFailureSpecification
import io.qalipsis.api.meters.steps.failure.impl.RateFailureConditionSpecImpl
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
    fun shouldFailWhen(block: RateFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Specification for a [RateMeterStepSpecification].
 *
 * @author Francisca Eze
 */
@Spec
data class RateMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> TrackedThresholdRatio,
) : RateMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, RateMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueFailureSpecification<Rate, Double>> = mutableListOf()

    override fun shouldFailWhen(block: RateFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val rateFailureConditionSpec = RateFailureConditionSpecImpl()
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
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> TrackedThresholdRatio
): RateMeterStepSpecification<INPUT> {
    val step = RateMeterStepSpecificationImpl(name, block)
    this.add(step)
    return step
}

/**
 * Represents a tracked ratio measurement between a benchmark value and a cumulative total value.
 *
 * @property benchmark the target value to compare against the total.
 * @property total the actual measured value to be compared with.
 *
 * @author Francisca Eze
 */
data class TrackedThresholdRatio(val benchmark: Double, val total: Double)