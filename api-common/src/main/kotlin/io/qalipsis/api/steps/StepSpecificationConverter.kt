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
