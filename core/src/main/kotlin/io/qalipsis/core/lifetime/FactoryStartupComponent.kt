package io.qalipsis.core.lifetime

/**
 *
 * Generic interface for services to load in the factories at startup.
 *
 * @author Eric Jessé
 */
interface FactoryStartupComponent {

    fun getStartupOrder() = 0

    fun init() = Unit

}
