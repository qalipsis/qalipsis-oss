package io.qalipsis.api.steps

import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import kotlin.reflect.KClass

/**
 * Step specification used to capture the type of the input data.
 *
 * For test only.
 *
 * @author Eric Jessé
 */
class InputTypeCaptor<I : Any, O : Any>(
    val inputClass: KClass<I>
) : AbstractStepSpecification<I, O, InputTypeCaptor<I, O>>()

/**
 * Creates a [InputTypeCaptor] having [outputClass] as output type.
 *
 */
inline fun <reified INPUT : Any, OUTPUT : Any> StepSpecification<*, INPUT, *>.captureInputAndReturn(
    @Suppress("UNUSED_PARAMETER") outputClass: KClass<OUTPUT>): InputTypeCaptor<INPUT, OUTPUT> {
    val step = InputTypeCaptor<INPUT, OUTPUT>(INPUT::class)
    this.add(step)
    return step
}

/**
 * Step specification of a step as root of scenario, generating integers.
 *
 * For test only.
 *
 * @author Eric Jessé
 */
class DummyStepSpecification : AbstractStepSpecification<Unit, Int, DummyStepSpecification>() {

    init {
        scenario = scenario("my-scenario") as StepSpecificationRegistry
    }
}

/**
 * Creates a [DummyStepSpecification] as next step.
 */
fun <INPUT> AbstractStepSpecification<*, INPUT, *>.dummy(): DummyStepSpecification {
    val step = DummyStepSpecification()
    this.add(step)
    return step
}
