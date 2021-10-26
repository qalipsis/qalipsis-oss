package io.qalipsis.api.processors

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.NamedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Singleton

/**
 * Annotation mapper abstracting Micronaut from plugin implementation.
 */
internal class StepDecoratorAnnotationMapper : NamedAnnotationMapper {

    override fun getName(): String {
        return "io.qalipsis.api.annotations.StepDecorator"
    }

    override fun map(annotation: AnnotationValue<Annotation>,
                     visitorContext: VisitorContext): MutableList<AnnotationValue<*>> {
        return mutableListOf(AnnotationValue.builder(Singleton::class.java).build())
    }
}
