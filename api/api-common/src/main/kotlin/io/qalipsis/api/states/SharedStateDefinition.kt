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

package io.qalipsis.api.states

import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.SharedStateName
import java.time.Duration

/**
 * Definition of a shared state,
 *
 * @author Eric Jessé
 */
data class SharedStateDefinition(
    /**
     * ID of the Minion attached to the state.
     */
    val minionId: MinionId,
    /**
     * Name of the shared state.
     */
    val sharedStateName: SharedStateName,
    /**
     * Time to live of the state until its eviction.
     * When no value is set, the behavior is undefined and let to the implementation of [SharedStateRegistry].
     */
    val timeToLive: Duration? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SharedStateDefinition

        if (minionId != other.minionId) return false
        if (sharedStateName != other.sharedStateName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = minionId.hashCode()
        result = 31 * result + sharedStateName.hashCode()
        return result
    }
}