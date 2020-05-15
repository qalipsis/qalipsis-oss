package io.evolue.test.time

import io.evolue.api.time.isLongerOrEqualTo
import io.evolue.api.time.isLongerThan
import io.evolue.api.time.isShorterOrEqualTo
import io.evolue.api.time.isShorterThan
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.fail
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 *
 * @author Eric Jessé
 */
object EvolueTimeAssertions {

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

fun measureTime(block: (() -> Unit)): Duration {
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