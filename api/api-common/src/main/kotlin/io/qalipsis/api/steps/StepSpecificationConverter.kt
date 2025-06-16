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

import io.micronaut.context.annotation.Requires

/**
 * Interface for the converters from [StepSpecification] to a concrete [Step].
 * Each kind of [StepSpecification] should have its own implementation.
 *
 * @author Eric Jessé
 */
@Requires(env = ["standalone", "factory"])
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
