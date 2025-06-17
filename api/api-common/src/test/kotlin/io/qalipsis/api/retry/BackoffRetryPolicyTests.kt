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

package io.qalipsis.api.retry

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isSameAs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.steps.TestStepContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 * Tests to validate the BackoffRetryPolicy
 *
 * @author Francisca Eze
 */
@WithMockk
internal class BackoffRetryPolicyTests {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @InjectMockKs
    private var context: TestStepContext<String, String> =
        TestStepContext(minionId = "345678", scenarioName = "current-scenario", stepName = "step-2")

    private val executable: suspend (StepContext<String, String>) -> Unit by lazy {
        { service.mockMethod() }
    }

    private val service = mockk<TestableService>()

    @Test
    @Timeout(5)
    internal fun `should retry and throw exception when it always fails`() = testCoroutineDispatcher.run {
        // given
        val backoffRetryPolicy = BackoffRetryPolicy()
        val exception = RuntimeException()
        every { service.mockMethod() } throws exception

        // when
        val before = System.currentTimeMillis()
        val caught = assertThrows<Exception> {
            backoffRetryPolicy.execute(context, executable)
        }
        val after = System.currentTimeMillis()

        // then
        assertThat(caught).isSameAs(exception)
        assertThat(after - before).isBetween(3000, 4400)
        coVerify(exactly = 4) { service.mockMethod() }
    }

    @Test
    @Timeout(5)
    internal fun `should retry until success`() = testCoroutineDispatcher.run {
        // given
        val backoffRetryPolicy = BackoffRetryPolicy()
        every { service.mockMethod() } throws RuntimeException() andThenThrows RuntimeException() andThen 23

        // when
        val before = System.currentTimeMillis()
        backoffRetryPolicy.execute(context, executable)
        val after = System.currentTimeMillis()

        // then
        assertThat(after - before).isBetween(2000, 2200)
        coVerify(exactly = 3) { service.mockMethod() }
    }

    @Test
    @Timeout(5)
    internal fun `should set no delay when it immediately succeeds`() = testCoroutineDispatcher.run {
        // given
        val backoffRetryPolicy = BackoffRetryPolicy()
        every { service.mockMethod() } returns 23

        // when
        val before = System.currentTimeMillis()
        backoffRetryPolicy.execute(context, executable)
        val after = System.currentTimeMillis()

        // then
        assertThat(after - before).isBetween(0, 1)
        coVerify(exactly = 1) { service.mockMethod() }
    }

    @Test
    @Timeout(5)
    internal fun `should apply the increasing delay independently for each call`() = testCoroutineDispatcher.run {
        // given
        val backoffRetryPolicy =
            BackoffRetryPolicy(delay = Duration.ofMillis(2), multiplier = 2.0, maxDelay = Duration.ofSeconds(2))
        val exception = RuntimeException()
        every { service.mockMethod() } throws exception

        // when
        val before = System.currentTimeMillis()
        val caught = assertThrows<Exception> {
            backoffRetryPolicy.execute(context, executable)
        }
        val after = System.currentTimeMillis()

        // then
        assertThat(caught).isSameAs(exception)
        assertThat(after - before).isBetween(10, 130)
        coVerify(exactly = 4) { service.mockMethod() }

        // when
        val before2 = System.currentTimeMillis()
        val caught2 = assertThrows<Exception> {
            backoffRetryPolicy.execute(context, executable)
        }
        val after2 = System.currentTimeMillis()

        // then
        assertThat(caught2).isSameAs(exception)
        assertThat(after2 - before2).isBetween(10, 140)
        coVerify(exactly = 8) { service.mockMethod() }
    }

    @Test
    @Timeout(5)
    internal fun `should retry with backoff delay, but no more than the max`() = testCoroutineDispatcher.run {
        // given
        val backoffRetryPolicy = BackoffRetryPolicy(
            retries = 6,
            maxDelay = Duration.ofMillis(5),
            delay = Duration.ofMillis(1),
            multiplier = 1000.0
        )
        val exception = RuntimeException()
        every { service.mockMethod() } throws exception

        // when
        val before = System.currentTimeMillis()
        val caught = assertThrows<Exception> {
            backoffRetryPolicy.execute(context, executable)
        }
        val after = System.currentTimeMillis()

        // then
        assertThat(caught).isSameAs(exception)
        assertThat(after - before).isBetween(30, 160)
        coVerify(exactly = 7) { service.mockMethod() }
    }
}

class TestableService {
    fun mockMethod(): Int {
        var fact = 1
        val number = (2..10).random()
        for (count in number downTo 2) {
            fact *= count
        }
        return fact
    }
}