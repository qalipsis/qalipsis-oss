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

import com.google.common.util.concurrent.AtomicDouble
import com.tdunning.math.stats.TDigest
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.factory.meters.MeterSnapshotImpl
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.DoubleAdder
import java.util.concurrent.atomic.LongAdder

/**
 * Implementation of distribution summary to determine the statistical distribution of events, that resets when snapshots are generated.
 *
 * @author Francisca Eze
 */
internal class StepDistributionSummary(
    override val id: Meter.Id,
    private val meterReporter: MeterReporter,
    override val percentiles: Collection<Double>,
) : DistributionSummary, Meter.ReportingConfiguration<DistributionSummary> {

    private var reportingConfigured = AtomicBoolean()

    @KTestable
    private val counter = LongAdder()

    @KTestable
    private val total = DoubleAdder()

    @KTestable
    private var tDigest: TDigest? = supplyIf(percentiles.isNotEmpty()) { TDigest.createMergingDigest(100.0) }

    private val max = AtomicDouble(0.0)

    override fun report(configure: Meter.ReportingConfiguration<DistributionSummary>.() -> Unit): DistributionSummary {
        if (!reportingConfigured.compareAndExchange(false, true)) {
            this.configure()
        }
        return this
    }

    override fun display(
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short,
        column: Short,
        toNumber: DistributionSummary.() -> Number,
    ) {
        meterReporter.report(this, format, severity, row, column, toNumber)
    }

    override suspend fun snapshot(timestamp: Instant): MeterSnapshot =
        MeterSnapshotImpl(timestamp, id.copy(tags = id.tags + ("scope" to "period")), measure())

    @KTestable
    private fun measure(): Collection<Measurement> {
        val result = listOf(
            MeasurementMetric(mean(), Statistic.MEAN),
            MeasurementMetric(counter.sumThenReset().toDouble(), Statistic.COUNT),
            MeasurementMetric(total.sumThenReset(), Statistic.TOTAL),
            MeasurementMetric(max.getAndSet(0.0), Statistic.MAX),
        ) + (percentiles.map {
            DistributionMeasurementMetric(percentile(it), Statistic.PERCENTILE, it)
        })
        tDigest?.let {
            synchronized(it) {
                tDigest = TDigest.createMergingDigest(100.0)
            }
        }
        return result
    }

    override suspend fun summarize(timestamp: Instant) = snapshot(timestamp)

    override fun count(): Long = counter.toLong()

    override fun totalAmount(): Double = total.toDouble()

    override fun max(): Double = max.get()

    override fun record(amount: Double) {
        if (amount > 0) {
            var curMax: Double
            do {
                curMax = max.get()
                // This do / while loop ensures that the max is only updated if not a higher value was set in the meantime.
            } while (curMax < amount && !max.compareAndSet(curMax, amount))
            total.add(amount)
            counter.add(1)
            tDigest?.let {
                synchronized(it) {
                    it.add(amount)
                }
            }
        }
    }

    override fun mean(): Double {
        val count = count()
        return if (count == 0L) 0.0 else (totalAmount() / count)
    }

    override fun percentile(percentile: Double): Double {
        return tDigest?.quantile(percentile / 100) ?: 0.0
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StepDistributionSummary
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}