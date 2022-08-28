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
