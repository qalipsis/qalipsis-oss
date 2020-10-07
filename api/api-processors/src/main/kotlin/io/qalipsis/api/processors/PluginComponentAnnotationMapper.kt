package io.qalipsis.api.processors

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.NamedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext
import javax.inject.Singleton

/**
 * Annotation mapper abstracting Micronaut from plugin implementation.
 */
internal class PluginComponentAnnotationMapper : NamedAnnotationMapper {

    override fun getName(): String {
        return "io.qalipsis.api.annotations.PluginComponent"
    }

    override fun map(annotation: AnnotationValue<Annotation>,
                     visitorContext: VisitorContext): MutableList<AnnotationValue<*>> {
        return mutableListOf(AnnotationValue.builder(Singleton::class.java).build())
    }
}
