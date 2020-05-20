package io.evolue.api.steps

import cool.graph.cuid.Cuid
import io.evolue.api.ScenarioSpecification
import io.evolue.api.context.CorrelationRecord
import io.evolue.api.context.StepName
import java.time.Duration

/**
 * Specification for a [io.evolue.core.factory.steps.CorrelationStep].
 *
 * @author Eric Jessé
 */
data class CorrelationStepSpecification<INPUT, OUTPUT>(
    val primaryKeyExtractor: CorrelationRecord<INPUT>.() -> Any?,
    val secondaryKeyExtractor: CorrelationRecord<out Any?>.() -> Any?,
    val secondaryStepName: StepName
) : AbstractStepSpecification<INPUT?, OUTPUT?, CorrelationStepSpecification<INPUT, OUTPUT>>() {

    var cacheTimeout: Duration = Duration.ofMinutes(1)

}

fun <INPUT, OTHER_INPUT : Any?> StepSpecification<*, INPUT, *>.correlate(on: CorrelationRecord<INPUT>.() -> Any?,
                                                                         with: ScenarioSpecification.() -> AbstractStepSpecification<*, OTHER_INPUT, *>,
                                                                         having: CorrelationRecord<OTHER_INPUT>.() -> Any?): CorrelationStepSpecification<INPUT, Pair<INPUT, OTHER_INPUT>> {
    val secondaryStep = scenario!!.with()
    if (secondaryStep.name.isNullOrBlank()) {
        secondaryStep.name = Cuid.createCuid()
    }
    this.scenario!!.register(secondaryStep)
    return correlate(on, secondaryStep.name!!, having)
}

fun <INPUT, OTHER_INPUT : Any?> StepSpecification<*, INPUT, *>.correlate(on: CorrelationRecord<INPUT>.() -> Any?,
                                                                         with: String,
                                                                         having: CorrelationRecord<OTHER_INPUT>.() -> Any?): CorrelationStepSpecification<INPUT, Pair<INPUT, OTHER_INPUT>> {
    val step = CorrelationStepSpecification<INPUT, Pair<INPUT, OTHER_INPUT>>(on,
        having as CorrelationRecord<out Any?>.() -> Any?, with)
    this.add(step)
    return step
}