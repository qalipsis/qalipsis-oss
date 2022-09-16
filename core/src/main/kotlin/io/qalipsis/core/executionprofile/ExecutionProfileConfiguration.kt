/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.executionprofile

import kotlinx.serialization.Serializable

/**
 * Configuration of the execution profile to apply to a scenario in a campaign.
 *
 * @property startOffsetMs time to wait before the first minion is executed, it should take the latency of the factories into consideration
 * @property speedFactor speed factor to apply on the execution profile, each strategy will apply it differently depending on its own implementation
 *
 * @author Eric Jessé
 */
@Serializable
data class ExecutionProfileConfiguration(
    val startOffsetMs: Long = 3000,
    val speedFactor: Double = 1.0,
)
