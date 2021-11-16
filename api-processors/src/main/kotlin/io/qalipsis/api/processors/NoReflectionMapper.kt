package io.qalipsis.api.processors

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.annotation.NamedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext

/**
 * Annotation mapper abstracting Micronaut from plugin implementation.
 */
internal class NoReflectionMapper : NamedAnnotationMapper {

    override fun getName(): String {
        return "io.qalipsis.api.annotations.NoReflection"
    }

    override fun map(
        annotation: AnnotationValue<Annotation>,
        visitorContext: VisitorContext
    ): MutableList<AnnotationValue<*>> {
        return mutableListOf(AnnotationValue.builder(Introspected::class.java).build())
    }
}
