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

package io.qalipsis.api

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.qalipsis.api.lang.doIf
import io.qalipsis.api.lang.doUnless
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.lang.supplyUnless
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.lang.tryAndLogOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicInteger

internal class OperatorsTest {


    private val outPrintStream: PrintStream = System.out

    /**
     * Resets the right console stream after the tests on the loggers.
     */
    @AfterEach
    internal fun tearDown() {
        System.setOut(outPrintStream)
    }

    @Test
    internal fun `supplyIf should provide when the condition is met`() {
        val result = supplyIf(true) { "my-value" }

        assertThat(result).isEqualTo("my-value")
    }

    @Test
    internal fun `supplyIf should not provide when the condition is not met`() {
        val result = supplyIf(false) { "my-value" }

        assertThat(result).isNull()
    }

    @Test
    internal fun `supplyUnless should provide when the condition is not met`() {
        val result = supplyUnless(false) { "my-value" }

        assertThat(result).isEqualTo("my-value")
    }

    @Test
    internal fun `supplyUnless should not provide when the condition is met`() {
        val result = supplyUnless(true) { "my-value" }

        assertThat(result).isNull()
    }

    @Test
    internal fun `doIf should execute when the condition is met`() {
        val counter = AtomicInteger()
        doIf(true) { counter.incrementAndGet() }

        assertThat(counter.get()).isEqualTo(1)
    }

    @Test
    internal fun `doIf should not execute when the condition is not met`() {
        val counter = AtomicInteger()
        doIf(false) { counter.incrementAndGet() }

        assertThat(counter.get()).isEqualTo(0)
    }

    @Test
    internal fun `doUnless should execute when the condition is not met`() {
        val counter = AtomicInteger()
        doUnless(false) { counter.incrementAndGet() }

        assertThat(counter.get()).isEqualTo(1)
    }

    @Test
    internal fun `doUnless should not execute when the condition is met`() {
        val counter = AtomicInteger()
        doUnless(true) { counter.incrementAndGet() }

        assertThat(counter.get()).isEqualTo(0)
    }

    @Test
    internal fun `tryAndLog should log the exception and throw it`() {
        val logStream = ByteArrayOutputStream()
        System.setOut(PrintStream(logStream))
        val logger = LoggerFactory.getLogger("my-test-logger")
        val exception = RuntimeException("My error message")

        val thrownException = assertThrows<RuntimeException> {
            tryAndLog(logger) {
                throw exception
            }
        }

        assertThat(logStream.toString()).all {
            contains("my-test-logger")
            contains("My error message")
        }
        assertThat(thrownException).isSameAs(exception)
    }

    @Test
    internal fun `tryAndLog should return a valid result`() {
        val logStream = ByteArrayOutputStream()
        System.setOut(PrintStream(logStream))
        val logger = LoggerFactory.getLogger("my-test-logger")

        val result = tryAndLog(logger) { 123 }

        assertThat(logStream.toString()).isEmpty()
        assertThat(result).isEqualTo(123)
    }

    @Test
    internal fun `tryAndLogOrNull should log the exception and return null`() {
        val logStream = ByteArrayOutputStream()
        System.setOut(PrintStream(logStream))
        val logger = LoggerFactory.getLogger("my-test-logger")
        val exception = RuntimeException("My error message")

        val result = tryAndLogOrNull(logger) {
            throw exception
        }

        assertThat(logStream.toString()).all {
            contains("my-test-logger")
            contains("My error message")
        }
        assertThat(result).isNull()
    }

    @Test
    internal fun `tryAndLogOrNull should return a valid result`() {
        val logStream = ByteArrayOutputStream()
        System.setOut(PrintStream(logStream))
        val logger = LoggerFactory.getLogger("my-test-logger")

        val result = tryAndLogOrNull(logger) { 123 }

        assertThat(logStream.toString()).doesNotContain("in io.qalipsis")
        assertThat(result).isEqualTo(123)
    }
}
