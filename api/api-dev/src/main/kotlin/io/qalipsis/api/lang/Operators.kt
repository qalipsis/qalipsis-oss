/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.lang

import org.slf4j.Logger

/**
 * Executes the block and returns the result if the condition is matched, null otherwise.
 *
 * @see supplyUnless
 */
inline fun <T> supplyIf(condition: Boolean, block: () -> T): T? {
    return if (condition) {
        block()
    } else {
        null
    }
}

/**
 * Executes the block and returns the result if the condition is not matched, null otherwise.
 *
 * @see supplyIf
 */
inline fun <T> supplyUnless(condition: Boolean, block: () -> T): T? {
    return if (!condition) {
        block()
    } else {
        null
    }
}

/**
 * Executes the block only if the condition is matched.
 *
 * @see doUnless
 */
inline fun doIf(condition: Boolean, block: () -> Any?) {
    if (condition) {
        block()
    }
}

/**
 * Executes the block only if the condition is not matched.
 *
 * @see doIf
 */
inline fun doUnless(condition: Boolean, block: () -> Any?) {
    if (!condition) {
        block()
    }
}

/**
 * Helper function to log any exception with the specified [logger] before throwing it or return the result when everything went well.
 *
 * @author Eric Jessé
 */
inline fun <T> tryAndLog(logger: Logger, block: () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        logger.error(e.message, e)
        throw e
    }
}

/**
 * Helper function to log any thrown exception with the specified [logger] and return null or return the result when everything went well.
 *
 * @author Eric Jessé
 */
inline fun <T> tryAndLogOrNull(logger: Logger, block: () -> T): T? {
    return try {
        block()
    } catch (e: Exception) {
        logger.error(e.message, e)
        null
    }
}

/**
 * Executes the statement only if the receiver is not null.
 *
 * @author Eric Jessé
 */
inline fun <T> T.alsoWhenNotNull(block: () -> Unit): T = this.also { if (this != null) block() }

/**
 * Executes the statement only if the receiver is null.
 *
 * @author Eric Jessé
 */
inline fun <T> T.alsoWhenNull(block: () -> Unit): T = this.also { if (this == null) block() }
