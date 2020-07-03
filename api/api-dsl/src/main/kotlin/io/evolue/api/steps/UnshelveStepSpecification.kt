package io.evolue.api.steps

/**
 * Specification for a [io.evolue.core.factory.steps.UnshelveStep].
 *
 * @author Eric Jessé
 */
data class UnshelveStepSpecification<INPUT, OUTPUT>(
        val names: List<String>,
        val delete: Boolean,
        val singular: Boolean
) : AbstractStepSpecification<INPUT, Pair<INPUT, OUTPUT?>, UnshelveStepSpecification<INPUT, OUTPUT>>()

fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.unshelve(name: String,
        delete: Boolean = true): UnshelveStepSpecification<INPUT, OUTPUT> {
    val step = UnshelveStepSpecification<INPUT, OUTPUT>(listOf(name), delete, true)
    this.add(step)
    return step
}

fun <INPUT> StepSpecification<*, INPUT, *>.unshelve(
        vararg names: String): UnshelveStepSpecification<INPUT, Map<String, Any?>> {
    val step = UnshelveStepSpecification<INPUT, Map<String, Any?>>(names.asList(), false, false)
    this.add(step)
    return step
}

fun <INPUT> StepSpecification<*, INPUT, *>.unshelveAndDelete(
        vararg names: String): UnshelveStepSpecification<INPUT, Map<String, Any?>> {
    val step = UnshelveStepSpecification<INPUT, Map<String, Any?>>(names.asList(), true, false)
    this.add(step)
    return step
}
