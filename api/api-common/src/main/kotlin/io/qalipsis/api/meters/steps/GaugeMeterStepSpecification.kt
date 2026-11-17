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
