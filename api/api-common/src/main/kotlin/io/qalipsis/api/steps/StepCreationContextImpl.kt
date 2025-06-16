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

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.scenario.StepSpecificationRegistry
import javax.validation.Valid

/**
 * Mutable context to convert a [StepSpecification] to a [Step].
 *
 * @author Eric Jessé
 */
@Introspected
class StepCreationContextImpl<SPEC : StepSpecification<*, *, *>>(

    override val scenarioSpecification: StepSpecificationRegistry,

    override val directedAcyclicGraph: DirectedAcyclicGraph,

    override val stepSpecification: @Valid SPEC

) : StepCreationContext<SPEC> {

    override var createdStep: Step<*, *>? = null

    override fun createdStep(step: Step<*, *>) {
        createdStep = step
    }

}
