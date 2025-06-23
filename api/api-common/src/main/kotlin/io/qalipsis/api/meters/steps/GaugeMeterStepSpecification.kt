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
import io.qalipsis.api.meters.steps.failure.GaugeFailureConditionSpec
import io.qalipsis.api.meters.steps.failure.impl.ComparableValueFailureSpecification
import io.qalipsis.api.meters.steps.failure.impl.GaugeFailureConditionSpecImpl
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification

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
    fun shouldFailWhen(block: GaugeFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Implementation of [GaugeMeterStepSpecification].
 *
 * @author Francisca Eze
 */
@Spec
data class GaugeMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : GaugeMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, GaugeMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueFailureSpecification<Gauge, Double>> = mutableListOf()

    override fun shouldFailWhen(block: GaugeFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val gaugeFailureConditionSpecImpl = GaugeFailureConditionSpecImpl()
        gaugeFailureConditionSpecImpl.block()
        checks = gaugeFailureConditionSpecImpl.checks
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
fun <INPUT> StepSpecification<*, INPUT, *>.gauge(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): GaugeMeterStepSpecification<INPUT> {
    val step = GaugeMeterStepSpecificationImpl(name, block)
    this.add(step)
    return step
}