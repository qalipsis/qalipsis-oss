package io.qalipsis.api

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
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

        assertThat(logStream.toString()).isEmpty()
        assertThat(result).isEqualTo(123)
    }
}
