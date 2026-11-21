/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.sync

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup

/**
 * Stresses a [SuspendedCountLatch] shared by the benchmark threads, the way the buffered publishers use it:
 * an increment before launching a job, a decrement when the job completes.
 *
 * The latch is verified after each iteration, failing the benchmark when a decrement was rejected or when the count
 * does not return to zero.
 */
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 2)
@Warmup(iterations = 3)
internal class SuspendedCountLatchJmH {

    private lateinit var executor: ExecutorService

    private lateinit var jobsScope: CoroutineScope

    private lateinit var latch: SuspendedCountLatch

    private val failure = AtomicReference<Throwable>()

    @Setup(Level.Iteration)
    fun setUp() {
        executor = Executors.newFixedThreadPool(JOBS_THREADS)
        jobsScope = CoroutineScope(
            executor.asCoroutineDispatcher() + CoroutineExceptionHandler { _, e -> failure.compareAndSet(null, e) }
        )
        latch = SuspendedCountLatch(0)
        failure.set(null)
    }

    @TearDown(Level.Iteration)
    fun verify() {
        runBlocking { latch.await() }
        executor.shutdown()
        failure.get()?.let { throw IllegalStateException("A decrement of the latch failed", it) }
        check(latch.get() == 0L) { "The latch count should be 0 but was ${latch.get()}" }
    }

    @Benchmark
    fun incrementThenDecrementInJob() {
        runBlocking {
            latch.increment()
            jobsScope.launch {
                try {
                    yield()
                } finally {
                    latch.decrement()
                }
            }
        }
    }

    private companion object {

        const val JOBS_THREADS = 4
    }
}
