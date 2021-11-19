package io.qalipsis.api.heads

/**
 *
 * Generic interface for services to load in the head at startup.
 *
 * @author Eric Jessé
 */
interface StartupHeadComponent {

    fun getStartupOrder() = 0

    fun init() = Unit

}

