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

package io.qalipsis.api.sync

import assertk.assertThat
import assertk.assertions.isGreaterThan
import io.qalipsis.api.lang.millis
import io.qalipsis.api.lang.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture

internal class SuspendedFutureForCompletionStageTest {

    @Test
    @Timeout(3)
    internal fun `should return the expected result`() = runBlocking {
        // given
        val completionStage = CompletableFuture<Unit>()
        val suspendedFuture = SuspendedFutureForCompletionStage(completionStage)
        val start = System.currentTimeMillis()
        launch {
            delay(200)
            completionStage.complete(Unit)
        }

        // when
        val result = suspendedFuture.get()

        // then
        assertSame(Unit, result)
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }

    @Test
    @Timeout(3)
    internal fun `should return the exception`() = runBlockingTest {
        // given
        val completionStage = CompletableFuture<Unit>()
        val suspendedFuture = SuspendedFutureForCompletionStage(completionStage)
        launch {
            delay(200)
            completionStage.completeExceptionally(RuntimeException("This is an error"))
        }

        // when + get
        assertThrows<RuntimeException> {
            suspendedFuture.get(100.millis())
        }
    }

    @Test
    @Timeout(3)
    internal fun `should return a timeout exception`() = runBlockingTest {
        // given
        val suspendedFuture = SuspendedFutureForCompletionStage(CompletableFuture<Unit>())

        // when + then
        assertThrows<TimeoutCancellationException> {
            suspendedFuture.get(100.millis())
        }
    }

    @Test
    @Timeout(3)
    internal fun `should return the result before the timeout`() = runBlocking {
        // given
        val completionStage = CompletableFuture<Unit>()
        val suspendedFuture = SuspendedFutureForCompletionStage(completionStage)
        val start = System.currentTimeMillis()
        launch {
            delay(200)
            completionStage.complete(Unit)
        }

        // when
        val result = suspendedFuture.get(50.seconds())

        // then
        assertSame(Unit, result)
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }

    @Test
    @Timeout(3)
    internal fun `should throw the exception before the timeout`() = runBlocking {
        // given
        val completionStage = CompletableFuture<Unit>()
        val suspendedFuture = SuspendedFutureForCompletionStage(completionStage)
        val start = System.currentTimeMillis()
        launch {
            delay(200)
            completionStage.completeExceptionally(RuntimeException("This is an error"))
        }

        // when + get
        assertThrows<RuntimeException> {
            suspendedFuture.get(50.seconds())
        }
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }
}
