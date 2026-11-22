/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.runtime.executors

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean

@Timeout(10)
internal class EventLoopsDispatcherTest {

    private lateinit var dispatcher: EventLoopsDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = EventLoopsDispatcher("test-loops", 4)
    }

    @AfterEach
    fun tearDown() {
        dispatcher.close()
    }

    @Test
    fun `given a task dispatched from another thread when it runs then it is executed by a loop of the dispatcher`() =
        runBlocking {
            val threadName = withContext(dispatcher) { Thread.currentThread().name }

            assertThat(threadName).startsWith("test-loops-")
        }

    @Test
    fun `given a coroutine running on a loop when it yields then it resumes on the same loop in order`() = runBlocking {
        val result = withContext(dispatcher) {
            val order = mutableListOf<Int>()
            val other = launch { order += 2 }
            order += 1
            yield()
            other.join()
            order += 3
            Thread.currentThread() to order
        }

        assertThat(result.first.name).startsWith("test-loops-")
        assertThat(result.second).containsExactly(1, 2, 3)
    }

    @Test
    fun `given a delay when it expires then the coroutine resumes on its loop after the delay`() = runBlocking {
        val (sameThread, elapsedMs) = withContext(dispatcher) {
            val thread = Thread.currentThread()
            val start = System.nanoTime()
            delay(DELAY_MS)
            (Thread.currentThread() === thread) to (System.nanoTime() - start) / 1_000_000
        }

        assertThat(sameThread).isTrue()
        assertThat(elapsedMs).isBetween(DELAY_MS, DELAY_MS + TOLERANCE_MS)
    }

    @Test
    fun `given many delays due at the same time when they expire then all the coroutines resume`() = runBlocking {
        val resumed = (1..TIMERS_COUNT).map { async(dispatcher) { delay(DELAY_MS); 1 } }.awaitAll().sum()

        assertThat(resumed).isEqualTo(TIMERS_COUNT)
    }

    @Test
    fun `given a cancelled delay when its deadline passes then the coroutine is not resumed`() = runBlocking {
        val resumed = AtomicBoolean(false)
        val job = launch(dispatcher) {
            delay(DELAY_MS)
            resumed.set(true)
        }

        job.cancel()
        job.join()
        withContext(dispatcher) { delay(DELAY_MS * 2) }

        assertThat(job.isCancelled).isTrue()
        assertThat(resumed.get()).isFalse()
    }

    @Test
    fun `given a timeout when the block is too slow then it is cancelled at the timeout`() = runBlocking {
        val never = CompletableDeferred<Unit>()

        assertThrows<TimeoutCancellationException> {
            runBlocking(dispatcher) { withTimeout(DELAY_MS) { never.await() } }
        }
    }

    @Test
    fun `given a closed dispatcher when a task is dispatched then its job is cancelled`() = runBlocking {
        dispatcher.close()

        val job = launch(dispatcher) { delay(DELAY_MS) }
        job.join()

        assertThat(job.isCancelled).isTrue()
    }

    private companion object {
        const val DELAY_MS = 50L
        const val TOLERANCE_MS = 40L
        const val TIMERS_COUNT = 10_000
    }
}
