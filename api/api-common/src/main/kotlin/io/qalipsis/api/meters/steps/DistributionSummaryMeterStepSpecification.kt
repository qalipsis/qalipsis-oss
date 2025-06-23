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
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.steps.failure.DistributionSummaryFailureConditionSpec
import io.qalipsis.api.meters.steps.failure.impl.ComparableValueFailureSpecification
import io.qalipsis.api.meters.steps.failure.impl.DistributionSummaryFailureConditionSpecImpl
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepSpecification

/**
 * Specification for a [io.qalipsis.core.factory.steps.meter.DistributionSummaryMeterStep] to allow for configuration of failure conditions.
 *
 * @author Francisca Eze
 */
interface DistributionSummaryMeterStepSpecification<INPUT> :
    StepSpecification<INPUT, INPUT, DistributionSummaryMeterStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, INPUT, DistributionSummaryMeterStepSpecification<INPUT>> {

    /**
     * Allows specification of failure conditions on the distribution summary meter.
     */
    fun shouldFailWhen(block: DistributionSummaryFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *>
}

/**
 * Implementation of [DistributionSummaryMeterStepSpecification].
 *
 * @author Francisca Eze
 */
@Spec
data class DistributionSummaryMeterStepSpecificationImpl<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : DistributionSummaryMeterStepSpecification<INPUT>,
    AbstractStepSpecification<INPUT, INPUT, DistributionSummaryMeterStepSpecification<INPUT>>() {

    var checks: MutableList<ComparableValueFailureSpecification<DistributionSummary, Double>> = mutableListOf()

    val percentiles: MutableSet<Double> = mutableSetOf()

    override fun shouldFailWhen(block: DistributionSummaryFailureConditionSpec.() -> Unit): ConfigurableStepSpecification<INPUT, INPUT, *> {
        val summaryFailureConditionSpecImpl = DistributionSummaryFailureConditionSpecImpl(percentiles)
        summaryFailureConditionSpecImpl.block()
        checks = summaryFailureConditionSpecImpl.checks
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
fun <INPUT> StepSpecification<*, INPUT, *>.summary(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): DistributionSummaryMeterStepSpecification<INPUT> {
    val step = DistributionSummaryMeterStepSpecificationImpl(name, block)
    this.add(step)
    return step
}