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

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup

@State(Scope.Thread)
@Fork(value = 1, warmups = 2)
@Warmup(iterations = 3)
internal class ImmutableSlotJmH {

    lateinit var emptySlot: ImmutableSlot<Unit>
    lateinit var fullSlot: ImmutableSlot<Unit>

    @Setup(Level.Invocation)
    fun setUp() {
        emptySlot = ImmutableSlot()
        fullSlot = ImmutableSlot(Unit)
    }

    @Benchmark
    fun set() {
        runBlocking {
            emptySlot.set(Unit)
        }
    }

    @Benchmark
    fun offer() {
        emptySlot.offer(Unit)
    }

    @Benchmark
    fun get() {
        runBlocking {
            fullSlot.get()
        }
    }

}
