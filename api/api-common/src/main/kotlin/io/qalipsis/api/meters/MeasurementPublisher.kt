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

/**
 * Handles publishing of meter snapshots to different databases and monitoring systems.
 *
 * @author Francisca Eze
 */
interface MeasurementPublisher {

    /**
     * Handles setup and initialization of registered measurement publishers.
     */
    suspend fun init() = Unit

    /**
     * Saves/publishes received meters to their corresponding publishing strategies.
     */
    suspend fun publish(meters: Collection<MeterSnapshot>)

    /**
     * Shuts down all running publishers.
     */
    suspend fun stop() = Unit
}