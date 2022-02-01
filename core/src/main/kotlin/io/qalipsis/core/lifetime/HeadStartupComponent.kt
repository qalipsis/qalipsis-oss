package io.qalipsis.core.lifetime

/**
 *
 * Generic interface for services to load in the head at startup.
 *
 * @author Eric Jessé
 */
interface HeadStartupComponent {

    fun getStartupOrder() = 0

    fun init() = Unit

}

