package io.qalipsis.api.sync

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isGreaterThan
import io.qalipsis.api.lang.millis
import io.qalipsis.api.lang.seconds
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture

internal class SuspendedFutureForCompletionStageTest {

    @Test
    @Timeout(3)
    internal fun `should return the expected result`() {
        // given
        val completionStage = CompletableFuture<Unit>()
        val suspendedFuture = SuspendedFutureForCompletionStage(completionStage)
        val start = System.currentTimeMillis()
        GlobalScope.launch {
            delay(200)
            completionStage.complete(Unit)
        }

        // when
        val result = runBlocking {
            suspendedFuture.get()
        }

        // then
        assertSame(Unit, result)
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }

    @Test
    @Timeout(3)
    internal fun `should return the exception`() {
        // given
        val completionStage = CompletableFuture<Unit>()
        val suspendedFuture = SuspendedFutureForCompletionStage(completionStage)
        val start = System.currentTimeMillis()
        GlobalScope.launch {
            delay(200)
            completionStage.completeExceptionally(RuntimeException("This is an error"))
        }

        // when + get
        assertThrows<RuntimeException> {
            runBlocking {
                suspendedFuture.get(100.millis())
            }
        }
    }

    @Test
    @Timeout(3)
    internal fun `should return a timeout exception`() {
        // given
        val suspendedFuture = SuspendedFutureForCompletionStage(CompletableFuture<Unit>())

        // when + then
        assertThrows<TimeoutCancellationException> {
            runBlocking {
                suspendedFuture.get(100.millis())
            }
        }
    }

    @Test
    @Timeout(3)
    internal fun `should return the result before the timeout`() {
        // given
        val completionStage = CompletableFuture<Unit>()
        val suspendedFuture = SuspendedFutureForCompletionStage(completionStage)
        val start = System.currentTimeMillis()
        GlobalScope.launch {
            delay(200)
            completionStage.complete(Unit)
        }

        // when
        val result = runBlocking {
            suspendedFuture.get(50.seconds())
        }

        // then
        assertSame(Unit, result)
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }

    @Test
    @Timeout(3)
    internal fun `should throw the exception before the timeout`() {
        // given
        val completionStage = CompletableFuture<Unit>()
        val suspendedFuture = SuspendedFutureForCompletionStage(completionStage)
        val start = System.currentTimeMillis()
        GlobalScope.launch {
            delay(200)
            completionStage.completeExceptionally(RuntimeException("This is an error"))
        }

        // when + get
        assertThrows<RuntimeException> {
            runBlocking {
                suspendedFuture.get(50.seconds())
            }
        }
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }
}
