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

package io.qalipsis.api.meters

import java.time.Instant

/**
 * Holds a snapshot of collected measurements over a given period for any given meter.
 *
 * @property meterId meter that is being measured
 * @property measurements list of measurements belonging to the meter sampled over a given time
 * @property timestamp represent the time instant in epoch seconds that the snapshot was taken
 *
 *  @author Francisca Eze
 */
interface MeterSnapshot {

    val meterId: Meter.Id

    val timestamp: Instant

    val measurements: Collection<Measurement>

    /**
     * Duplicates the [MeterSnapshot] with a different [meterId].
     */
    fun duplicate(
        meterId: Meter.Id = this.meterId
    ): MeterSnapshot

}