package io.qalipsis.test.coroutines

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.opentest4j.AssertionFailedError

internal class TestDispatcherProviderTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    @Timeout(3)
    fun `should run the test totally and fail`() {
        // when
        val before = System.currentTimeMillis()
        val exception = assertThrows<AssertionFailedError> {
            testDispatcherProvider.run {
                delay(500)
                fail("The test should fail")
            }
        }
        val after = System.currentTimeMillis()

        // then
        assertThat(after - before).isGreaterThan(400L)
        assertThat(exception.message).isEqualTo("The test should fail")
    }

    @Test
    @Timeout(3)
    fun `should run the test totally and succeed`() {
        // when
        val suspendedCountLatch = SuspendedCountLatch(1)
        val before = System.currentTimeMillis()
        testDispatcherProvider.run {
            delay(500)
            suspendedCountLatch.decrement()
        }
        val after = System.currentTimeMillis()

        // then
        assertThat(suspendedCountLatch.get()).isEqualTo(0L)
        assertThat(after - before).isGreaterThan(400L)
    }

    @Test
    @Timeout(3)
    fun `should run the test totally and leave even if children coroutines are running`() {
        // when
        val suspendedCountLatch = SuspendedCountLatch(1)
        val before = System.currentTimeMillis()
        testDispatcherProvider.run {
            launch {
                delay(10000)
            }
            suspendedCountLatch.decrement()
        }
        val after = System.currentTimeMillis()

        // then
        assertThat(suspendedCountLatch.get()).isEqualTo(0L)
        assertThat(after - before).isLessThan(100L)
    }
}