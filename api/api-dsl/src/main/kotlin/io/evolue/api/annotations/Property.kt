package io.evolue.api.annotations

/**
 * Annotation to mark an argument of a scenario class or method to read its value from the configuration and inject
 * at startup.
 *
 * @author Eric Jessé
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Property(
        val value: String = ""
)