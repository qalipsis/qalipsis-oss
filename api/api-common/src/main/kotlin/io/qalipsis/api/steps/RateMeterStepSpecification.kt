package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.RateFailureConditionSpec
import io.qalipsis.api.meters.RateFailureConditionSpecImpl
import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification

/**
 * @TODO
 * Specification for a [io.qalipsis.core.factory.steps.TimerStep].
 *
 * @author Francisca Eze
 */
@Introspected
data class RateMeterStepSpecification<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : AbstractStepSpecification<INPUT, INPUT, RateMeterStepSpecification<INPUT>>() {
    lateinit var checks: MutableList<ComparableValueFailureSpecification<Rate, Double>>

    fun shouldFailWhen(block: RateFailureConditionSpec.() -> INPUT): StepSpecification<*, INPUT, *> {
        val rateFailureConditionSpec = RateFailureConditionSpecImpl()
        rateFailureConditionSpec.block()
        checks = rateFailureConditionSpec.checks
        return this
    }

}

fun <INPUT> StepSpecification<*, INPUT, *>.rate(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): RateMeterStepSpecification<INPUT> {
    val step = RateMeterStepSpecification(name, block)
    this.add(step)
    return step
}