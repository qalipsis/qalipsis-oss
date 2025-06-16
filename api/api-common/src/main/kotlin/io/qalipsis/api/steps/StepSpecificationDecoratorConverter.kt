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
