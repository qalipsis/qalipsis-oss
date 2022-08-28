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