package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.GaugeFailureConditionSpec
import io.qalipsis.api.meters.GaugeFailureConditionSpecImpl
import io.qalipsis.api.meters.specification.ComparableValueFailureSpecification

/**
 * @TODO
 * Specification for a [io.qalipsis.core.factory.steps.TimerStep].
 *
 * @author Francisca Eze
 */
@Introspected
data class GaugeMeterStepSpecification<INPUT>(
    val meterName: String,
    val block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double,
) : AbstractStepSpecification<INPUT, INPUT, GaugeMeterStepSpecification<INPUT>>() {
    lateinit var checks: MutableList<ComparableValueFailureSpecification<Gauge, Double>>

    fun shouldFailWhen(block: GaugeFailureConditionSpec.() -> INPUT): StepSpecification<*, INPUT, *> {
        val gaugeFailureConditionSpecImpl = GaugeFailureConditionSpecImpl()
        gaugeFailureConditionSpecImpl.block()
        checks = gaugeFailureConditionSpecImpl.checks
        return this
    }

}

fun <INPUT> StepSpecification<*, INPUT, *>.gauge(
    name: String,
    block: (stepContext: StepContext<INPUT, INPUT>, input: INPUT) -> Double
): GaugeMeterStepSpecification<INPUT> {
    val step = GaugeMeterStepSpecification(name, block)
    this.add(step)
    return step
}