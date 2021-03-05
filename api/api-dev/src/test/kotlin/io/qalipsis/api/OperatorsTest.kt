package io.qalipsis.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.qalipsis.api.lang.coDoIf
import io.qalipsis.api.lang.coDoUnless
import io.qalipsis.api.lang.coSupplyIf
import io.qalipsis.api.lang.coSupplyUnless
import io.qalipsis.api.lang.doIf
import io.qalipsis.api.lang.doUnless
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.lang.supplyUnless
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

internal class OperatorsTest {

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
    internal fun `coSupplyIf should provide when the condition is met`() = runBlockingTest {
        val result = coSupplyIf(true) {
            delay(200)
            "my-value"
        }

        assertThat(result).isEqualTo("my-value")
    }

    @Test
    internal fun `coSupplyIf should not provide when the condition is not met`() = runBlockingTest {
        val result = coSupplyIf(false) {
            delay(200)
            "my-value"
        }

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
    internal fun `coSupplyUnless should provide when the condition is not met`() = runBlockingTest {
        val result = coSupplyUnless(false) {
            delay(200)
            "my-value"
        }

        assertThat(result).isEqualTo("my-value")
    }

    @Test
    internal fun `coSupplyUnless should not provide when the condition is met`() = runBlockingTest {
        val result = coSupplyUnless(true) {
            delay(200)
            "my-value"
        }

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
    internal fun `coDoIf should execute when the condition is met`() = runBlockingTest {
        val counter = SuspendedCountLatch()
        coDoIf(true) { counter.increment() }

        assertThat(counter.get()).isEqualTo(1)
    }

    @Test
    internal fun `coDoIf should not execute when the condition is not met`() = runBlockingTest {
        val counter = SuspendedCountLatch()
        coDoIf(false) { counter.increment() }

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
    internal fun `coDoUnless should execute when the condition is not met`() = runBlockingTest {
        val counter = SuspendedCountLatch()
        coDoUnless(false) { counter.increment() }

        assertThat(counter.get()).isEqualTo(1)
    }

    @Test
    internal fun `coDoUnless should not execute when the condition is met`() = runBlockingTest {
        val counter = SuspendedCountLatch()
        coDoUnless(true) { counter.increment() }

        assertThat(counter.get()).isEqualTo(0)
    }
}
