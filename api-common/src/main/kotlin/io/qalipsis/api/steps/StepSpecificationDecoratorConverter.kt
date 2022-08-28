/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

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
