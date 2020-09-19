package io.evolue.api.steps

/**
 * Specification for a [io.evolue.core.factories.steps.ShelveStep].
 *
 * @author Eric Jessé
 */
data class ShelveStepSpecification<INPUT>(
        val specification: (input: INPUT) -> Map<String, Any?>
) : AbstractStepSpecification<INPUT, INPUT, ShelveStepSpecification<INPUT>>()

/**
 * Shelves the result of [specification] into a cache for later use.
 *
 * @param specification the rule to extract from the input the set of key/values to shelve. The keys of the map are used as name to later [unshelve] the value.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.shelve(
        specification: (input: INPUT) -> Map<String, Any?>): ShelveStepSpecification<INPUT> {
    val step = ShelveStepSpecification(specification)
    this.add(step)
    return step
}

/**
 * Shelves the input into a cache for later use with the given name.
 *
 * @param name name of the value to later [unshelve] in the cache.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.shelve(name: String): ShelveStepSpecification<INPUT> {
    return this.shelve { input -> mapOf(name to input) }
}
