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
