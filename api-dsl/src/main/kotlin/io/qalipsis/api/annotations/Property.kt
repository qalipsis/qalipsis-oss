package io.qalipsis.api.annotations

/**
 * Annotation to mark an argument of a scenario class or method to read its value from the configuration and inject
 * at startup.
 *
 * @author Eric Jessé
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Property(
    /**
     * Name of the property to map onto the parameter.
     */
    val name: String = "",

    /**
     * Default value if the property is absent.
     */
    val orElse: String = ""
)
