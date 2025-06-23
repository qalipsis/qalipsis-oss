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
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.steps.failure.ThroughputFailureConditionSpec
import io.qalipsis.api.meters.steps.failure.impl.ComparableValueFailureSpecification
import io.qalipsis.api.meters.steps.failure.impl.ThroughputFailureConditionSpecImpl
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
    fun shouldFailWhen(block: ThroughputFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>

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

    var checks: MutableList<ComparableValueFailureSpecification<Throughput, Double>> = mutableListOf()

    val percentiles: MutableSet<Double> = mutableSetOf()

    override fun shouldFailWhen(block: ThroughputFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val throughputFailureConditionSpec = ThroughputFailureConditionSpecImpl(percentiles)
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
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): ThroughputMeterStepSpecification<INPUT> {
    val step = ThroughputMeterStepSpecificationImpl(name, block)
    this.add(step)
    return step
}