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

package io.qalipsis.core.factory.meters.inmemory.composite

import io.mockk.mockk
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.factory.meters.inmemory.DoubleProvider
import io.qalipsis.core.factory.meters.inmemory.valued.InMemoryCumulativeDistributionSummary
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
internal class CompositeDistributionSummaryWithoutPercentilesJmH {

    lateinit var meter: CompositeDistributionSummary

    @Setup(Level.Iteration)
    fun setUp() {
        meter = CompositeDistributionSummary(
            InMemoryCumulativeDistributionSummary(mockk<Meter.Id>(), mockk<MeterReporter>(), emptySet()),
            InMemoryCumulativeDistributionSummary(mockk<Meter.Id>(), mockk<MeterReporter>(), emptySet())
        )

    }

    @Benchmark
    fun record(doubleProvider: DoubleProvider) {
        meter.record(doubleProvider.value())
    }

}