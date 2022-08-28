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

package io.qalipsis.api.processors

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.NamedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Singleton

/**
 * Annotation mapper abstracting Micronaut from plugin implementation.
 */
class StepDecoratorAnnotationMapper : NamedAnnotationMapper {

    override fun getName(): String {
        return "io.qalipsis.api.annotations.StepDecorator"
    }

    override fun map(
        annotation: AnnotationValue<Annotation>,
        visitorContext: VisitorContext
    ): MutableList<AnnotationValue<*>> {
        return mutableListOf(AnnotationValue.builder(Singleton::class.java).build())
    }
}
