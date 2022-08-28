/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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