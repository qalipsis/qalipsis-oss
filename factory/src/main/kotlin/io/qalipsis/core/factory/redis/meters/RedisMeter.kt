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

package io.qalipsis.core.factory.redis.meters

import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.reporter.MeterReporter
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Meter recording locally and combined in Redis with the same meter of the other factories, on all its [levels].
 *
 * The values are recorded without any Redis request: the flush periodically drains them into the levels, sends
 * them in bulk and refreshes the cached combined values. The reading methods return the cached combined value of
 * the first level plus the local values not flushed yet.
 *
 * The snapshots are not created by the meter: only the owner of each level publishes them, from the values received
 * from Redis.
 *
 * @property id identifier of the meter, as created by the steps
 * @property levels levels combining the meter in Redis, the meter itself first, then its wider levels. For a meter
 *   tagged `scenario=checkout, zone=eu, method=GET`, the levels are:
 *   - `scenario=checkout, zone=eu, method=GET`: the meter itself, read by the meter,
 *   - `scenario=checkout, method=GET`: the meter in all the zones of the scenario,
 *   - `method=GET`: the meter in all the scenarios of the campaign.
 *
 *   A meter without scenario tag only has its own level. The local records are drained into all the levels, so that
 *   each level sums the meters of all the zones and scenarios it contains, over all the factories. A level is shared
 *   by all the meters having it in common: both `zone=eu` and `zone=us` contribute to `scenario=checkout, method=GET`.
 * @param meterReporter reporter displaying the meter in the campaign report, when configured with [report]
 */
internal abstract class RedisMeter<SELF : Meter<SELF>>(
    override val id: Meter.Id,
    val levels: List<RedisMeterLevel>,
    private val meterReporter: MeterReporter,
) : Meter<SELF>, Meter.ReportingConfiguration<SELF> {

    private val reportingConfigured = AtomicBoolean()

    protected val level: RedisMeterLevel = levels.first()

    /**
     * Moves the local values recorded since the latest call into the pending deltas of all the levels.
     * The heavy values (histograms) are only drained when [complete] is true, at the end of each period.
     */
    abstract fun drainInto(levels: List<RedisMeterLevel>, complete: Boolean)

    fun drain(complete: Boolean) = drainInto(levels, complete)

    @Suppress("UNCHECKED_CAST")
    override fun report(configure: Meter.ReportingConfiguration<SELF>.() -> Unit): SELF {
        if (reportingConfigured.compareAndSet(false, true)) {
            this.configure()
        }
        return this as SELF
    }

    @Suppress("UNCHECKED_CAST")
    override fun display(
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short,
        column: Short,
        toNumber: SELF.() -> Number,
    ) {
        meterReporter.report(this as SELF, format, severity, row, column, toNumber)
    }

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> = emptyList()

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> = emptyList()

    override fun equals(other: Any?) = this === other || (other is RedisMeter<*> && javaClass == other.javaClass && id == other.id)

    override fun hashCode() = id.hashCode()

    override fun toString() = "${javaClass.simpleName}(id=$id)"

    companion object {

        const val COUNT_FIELD = "cnt"

        const val VALUE_FIELD = "val"

        const val NUMERATOR_FIELD = "num"

        const val DENOMINATOR_FIELD = "den"

        const val TOTAL_FIELD = "tot"

        const val MAX_FIELD = RedisMeterLevel.MAX_FIELD

        const val BUCKETS_FIELD = "b"

        const val HITS_FIELD = "h"

        const val INTERVALS_FIELD = "n"

        const val FOLDED_HITS_FIELD = "hs"

        const val FOLDED_MAX_FIELD = "mx"

        fun List<RedisMeterLevel>.addPending(field: String, delta: Double) = forEach { it.addPending(field, delta) }
    }
}
