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

package io.qalipsis.core.factory.meters.inmemory.unpublished

import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import java.time.Instant

/**
 * Implementation of [Gauge] that does not generate any [MeterSnapshot] in order to avoid duplicated
 * published values for global meters.
 */
@Suppress("UNCHECKED_CAST")
class UnpublishedGauge(
    private val meter: Gauge,
) : Gauge by meter,
    Meter.ReportingConfiguration<Gauge> by (meter as Meter.ReportingConfiguration<Gauge>) {

    override suspend fun snapshot(timestamp: Instant): Collection<MeterSnapshot> {
        return emptyList()
    }

    override suspend fun summarize(timestamp: Instant): Collection<MeterSnapshot> {
        return emptyList()
    }

}