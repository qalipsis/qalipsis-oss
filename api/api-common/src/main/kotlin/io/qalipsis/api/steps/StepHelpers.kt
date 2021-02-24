package io.qalipsis.api.steps

import org.slf4j.Logger

/**
 * Helper function to log any thrown exception with the specified [logger] or return the result when everything went well.
 *
 * @author Eric Jessé
 */
fun <T> tryAndLog(logger: Logger, block: () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        logger.error(e.message, e)
        throw e
    }
}

/**
 * Helper function to log any thrown exception with the specified [logger] or return the result when everything went well.
 * Contrary to [tryAndLog], this function can be used in a coroutine context.
 *
 * @author Eric Jessé
 */
suspend fun <T> coTryAndLog(logger: Logger, block: suspend () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        logger.error(e.message, e)
        throw e
    }
}
