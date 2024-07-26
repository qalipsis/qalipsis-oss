/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters.inmemory

import io.mockk.mockk
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.reporter.MeterReporter
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup

@State(Scope.Benchmark)
@Fork(value = 1, warmups = 2)
@Warmup(iterations = 3)
internal class CompositeRateJmH {

    lateinit var meter: CompositeRate

    @Setup(Level.Iteration)
    fun setUp() {
        meter = CompositeRate(
            InMemoryCumulativeRate(mockk<Meter.Id>(), mockk<MeterReporter>()),
            InMemoryCumulativeRate(mockk<Meter.Id>(), mockk<MeterReporter>())
        )
    }

    @Benchmark
    fun incrementTotal() {
        meter.incrementTotal()
    }

    @Benchmark
    fun decrementTotal() {
        meter.decrementTotal()
    }


    @Benchmark
    fun incrementTotalWithValue(doubleProvider: DoubleProvider) {
        meter.incrementTotal(doubleProvider.value())
    }

    @Benchmark
    fun decrementTotalWithValue(doubleProvider: DoubleProvider) {
        meter.decrementTotal(doubleProvider.value() )
    }

    @Benchmark
    fun incrementBenchmark() {
        meter.incrementBenchmark()
    }

    @Benchmark
    fun decrementBenchmark() {
        meter.decrementBenchmark()
    }


    @Benchmark
    fun incrementBenchmarkWithValue(doubleProvider: DoubleProvider) {
        meter.incrementTotal(doubleProvider.value())
    }

    @Benchmark
    fun decrementBenchmarkWithValue(doubleProvider: DoubleProvider) {
        meter.decrementTotal(doubleProvider.value())
    }

}