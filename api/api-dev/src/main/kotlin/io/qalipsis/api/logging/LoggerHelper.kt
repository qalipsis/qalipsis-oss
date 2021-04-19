package io.qalipsis.api.logging

import mu.KotlinLogging

/**
 * Helper to support logging facilities.
 *
 * @author Eric Jessé
 */
object LoggerHelper {

    /**
     * Creates / returns an instance of logger for the caller class.
     */
    inline fun <reified T : Any> T.logger() = KotlinLogging.logger { }
}
