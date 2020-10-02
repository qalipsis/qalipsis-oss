package io.evolue.api.annotations

/**
 * Annotation to mark all the classes implied in the implementation of a specification.
 *
 * Among all supported operations, is the validation injection at compile-time for faster startup and removal of reflection.
 *
 * @author Eric Jessé
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Spec
