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

package io.qalipsis.api.executionprofile

/**
 * Ramp-up of minions on a scenario are defined as a sequence of starts, which are described by a [MinionsStartingLine].
 *
 * @see io.qalipsis.core.factory.orchestration.rampup.RampUpStrategy
 * @see io.qalipsis.core.factory.orchestration.rampup.RampUpStrategyIterator
 *
 * @author Eric Jessé
 */
data class MinionsStartingLine(
    /**
     * Number of minions to start on the next starting line.
     */
    val count: Int,

    /**
     * Offset of the start, related to the first start of the sequence.
     */
    val offsetMs: Long
)
