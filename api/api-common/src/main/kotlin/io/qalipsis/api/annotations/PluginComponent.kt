package io.qalipsis.api.annotations

/**
 * Annotation to mark the different components of a plugin as components to create them automatically at runtime.
 *
 * @author Eric Jessé
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PluginComponent
