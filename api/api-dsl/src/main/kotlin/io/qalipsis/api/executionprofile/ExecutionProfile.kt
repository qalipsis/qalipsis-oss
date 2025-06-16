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

import java.time.Duration

/**
 *
 * [ExecutionProfile] is an accessor to a [ExecutionProfileIterator]. The [ExecutionProfile] is part of the definition of the
 * scenario and defines the pace to start the minions.
 *
 * @author Eric Jessé
 */
interface ExecutionProfile {

    /**
     * Notifies the execution profile that the campaign is starting.
     */
    fun notifyStart(speedFactor: Double) = Unit

    /**
     * Generates a new [ExecutionProfileIterator] to define a new sequence of starts.
     *
     * @param totalMinionsCount the total number of minions that will be started for the scenario.
     * @param speedFactor the factor to accelerate (when greater than 1) or slower (between 0 and 1) the ramp-up.
     */
    fun iterator(totalMinionsCount: Int, speedFactor: Double): ExecutionProfileIterator

    /**
     * Verifies whether the completed minion, can be restarted.
     *
     * @param minionExecutionDuration the total duration the minion required to execute the full scenario
     */
    fun canReplay(minionExecutionDuration: Duration): Boolean = false
}
