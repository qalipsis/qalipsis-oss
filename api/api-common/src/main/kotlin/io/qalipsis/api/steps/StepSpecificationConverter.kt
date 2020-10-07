package io.qalipsis.api.steps

/**
 * Interface for the converters from [StepSpecification] to a concrete [Step].
 * Each kind of [StepSpecification] should have its own implementation.
 *
 * @author Eric Jessé
 */
interface StepSpecificationConverter<SPEC : StepSpecification<*, *, *>> {

    /**
     * Verify of the provided specification is supported by the converter.
     *
     * @return true when the converter is able to convert the provided specification.
     */
    fun support(stepSpecification: StepSpecification<*, *, *>): Boolean = true

    /**
     * Add the step described by the [StepCreationContext] to the context.
     */
    suspend fun <I, O> convert(creationContext: StepCreationContext<SPEC>)

}