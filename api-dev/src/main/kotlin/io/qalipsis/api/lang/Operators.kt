/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
