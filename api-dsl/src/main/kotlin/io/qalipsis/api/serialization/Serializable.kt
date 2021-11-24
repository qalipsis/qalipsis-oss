package io.qalipsis.api.serialization

import javax.validation.constraints.NotEmpty
import kotlin.reflect.KClass

/**
 * Annotation to set on classes or files to trigger the creation of a QALIPSIS serialization wrapper, for types
 * supporting the native kotlin serialization, but compiled in third-parties libraries.
 *
 * Classes annotated with the [kotlinx.serialization.Serializable] and compiled with the QALIPSIS processors
 * library in the Kapt classpath do not need to additionally support [Serializable].
 *
 * See [the official documentation](https://kotlinlang.org/docs/serialization.html) for more details.
 *
 * @property types types for which a QALIPSIS serialization wrapper should be created, they should not have generic types
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
annotation class Serializable(

    @get:NotEmpty
    val types: Array<KClass<*>>
)
