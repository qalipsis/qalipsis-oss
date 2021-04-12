package io.qalipsis.api.steps

/**
 * Special kind of [StepSpecificationConverter] for step decorators.
 *
 * @author Eric Jessé
 */
abstract class StepSpecificationDecoratorConverter<SPEC : StepSpecification<*, *, *>> {

    /**
     * Decorate the step according to the specification.
     */
    abstract suspend fun decorate(creationContext: StepCreationContext<SPEC>)

    /**
     * Order of the converter in the complete processing chain.
     */
    open val order: Int = LOWEST_PRECEDENCE

    companion object {

        /**
         * Constant for the highest precedence value, placing the decorator closer from the decorated step.
         */
        const val HIGHEST_PRECEDENCE = Int.MIN_VALUE

        /**
         * Constant for the lowest precedence value, placing the decorator further from the decorated step
         */
        const val LOWEST_PRECEDENCE = Int.MAX_VALUE

    }
}
