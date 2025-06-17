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

package io.qalipsis.api.steps

import io.qalipsis.api.annotations.Spec

/**
 * Configuration of the metrics and events to record for a step.
 *
 * @property events when set to true, records the events, defaults to false.
 * @property meters when set to true, records meters, defaults to false.
 *
 * @author Eric Jessé
 */
@Spec
data class StepMonitoringConfiguration(
    var events: Boolean = false,
    var meters: Boolean = false
) {
    /**
     * Enables the record of both events and meters.
     */
    fun all() {
        events = true
        meters = true
    }
}
