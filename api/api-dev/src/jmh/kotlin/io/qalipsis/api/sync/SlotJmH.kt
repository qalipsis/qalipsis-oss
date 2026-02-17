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
internal class SlotJmH {

    lateinit var emptySlot: Slot<Unit>
    lateinit var fullSlot: Slot<Unit>

    @Setup(Level.Invocation)
    fun setUp() {
        emptySlot = Slot()
        fullSlot = Slot(Unit)
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
