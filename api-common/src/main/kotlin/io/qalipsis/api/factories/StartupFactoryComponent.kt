package io.qalipsis.api.factories

/**
 *
 * Generic interface for services to load in the factories at startup.
 *
 * @author Eric Jessé
 */
interface StartupFactoryComponent {

    fun getStartupOrder() = 0

    fun init() = Unit

}
