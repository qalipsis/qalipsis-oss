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

package io.qalipsis.test.time

import io.qalipsis.api.lang.isLongerOrEqualTo
import io.qalipsis.api.lang.isLongerThan
import io.qalipsis.api.lang.isShorterOrEqualTo
import io.qalipsis.api.lang.isShorterThan
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.fail
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 *
 * @author Eric Jessé
 */
object QalipsisTimeAssertions {

    fun assertLongerThan(expected: Duration, actual: Duration, message: String? = null) {
        if (!actual.isLongerThan(expected)) {
            fail(
                "${message?.let { "${it}: " } ?: ""}${message?.let { "${it}: " } ?: ""}expected longer than $expected but was $actual")
        }
    }

    fun assertLongerOrEqualTo(expected: Duration, actual: Duration, message: String? = null) {
        if (!actual.isLongerOrEqualTo(expected)) {
            fail("${message?.let { "${it}: " } ?: ""}expected longer or equal to $expected but was $actual")
        }
    }

    fun assertShorterThan(expected: Duration, actual: Duration, message: String? = null) {
        if (!actual.isShorterThan(expected)) {
            fail("${message?.let { "${it}: " } ?: ""}expected shorter than $expected but was $actual")
        }
    }

    fun assertShorterOrEqualTo(expected: Duration, actual: Duration, message: String? = null) {
        if (!actual.isShorterOrEqualTo(expected)) {
            fail("${message?.let { "${it}: " } ?: ""}expected shorter or equal to $expected but was $actual")
        }
    }

    fun assertBefore(expected: Instant, actual: Instant, message: String? = null) {
        if (!actual.isBefore(expected)) {
            fail("${message?.let { "${it}: " } ?: ""}expected before than $expected but was $actual")
        }
    }

    fun assertBeforeOrEqual(expected: Instant, actual: Instant, message: String? = null) {
        if (actual.isAfter(expected)) {
            fail("${message?.let { "${it}: " } ?: ""}expected before or equal to $expected but was $actual")
        }
    }

    fun assertAfter(expected: Instant, actual: Instant, message: String? = null) {
        if (!actual.isAfter(expected)) {
            fail("${message?.let { "${it}: " } ?: ""}expected after than $expected but was $actual")
        }
    }

    fun assertAfterOrEqual(expected: Instant, actual: Instant, message: String? = null) {
        if (actual.isBefore(expected)) {
            fail("${message?.let { "${it}: " } ?: ""}expected after or equal to $expected but was $actual")
        }
    }


    fun assertInstantBefore(expected: Long, actual: Long, message: String? = null) {
        if (actual >= expected) {
            fail(
                "${message?.let { "${it}: " } ?: ""}expected before than $expected but was $actual (difference = ${actual - expected})")
        }
    }

    fun assertInstantBeforeOrEqual(expected: Long, actual: Long, message: String? = null) {
        if (actual > expected) {
            fail(
                "${message?.let { "${it}: " } ?: ""}expected before or equal to $expected but was $actual (difference = ${actual - expected})")
        }
    }

    fun assertInstantAfter(expected: Long, actual: Long, message: String? = null) {
        if (actual <= expected) {
            fail(
                "${message?.let { "${it}: " } ?: ""}expected after than $expected but was $actual (difference = ${actual - expected})")
        }
    }

    fun assertInstantAfterOrEqual(expected: Long, actual: Long, message: String? = null) {
        if (actual < expected) {
            fail(
                "${message?.let { "${it}: " } ?: ""}expected after or equal to $expected but was $actual (difference = ${actual - expected})")
        }
    }
}

inline fun measureTime(block: (() -> Unit)): Duration {
    val start = System.currentTimeMillis()
    try {
        block()
    } finally {
        return Duration.ofMillis(System.currentTimeMillis() - start)
    }
}

fun coMeasureTime(block: suspend (() -> Unit)): Duration {
    val start = AtomicReference<Long?>(null)
    val end = AtomicReference<Long?>(null)
    try {
        runBlocking {
            start.set(System.currentTimeMillis())
            block()
            end.set(System.currentTimeMillis())
        }
    } finally {
        val endNano = end.get() ?: System.currentTimeMillis()
        return Duration.ofMillis(endNano - start.get()!!)
    }
}
