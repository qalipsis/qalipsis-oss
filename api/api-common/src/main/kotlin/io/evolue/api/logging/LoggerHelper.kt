package io.evolue.api.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Helper to support logging facilities.
 *
 * @author Eric Jessé
 */
object LoggerHelper {

    /**
     * Creates / returns an instance of logger for the caller class.
     */
    inline fun <reified T : Any> T.logger(): Logger =
        LoggerFactory.getLogger(T::class.java.enclosingClass ?: T::class.java)
}