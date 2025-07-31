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

package io.qalipsis.core.head.redis.campaign

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of all the states managed by the [RedisCampaignExecutor].
 */
data class CampaignRedisState(val name: String) {

    override fun toString(): String = name

    companion object {

        val FACTORY_DAGS_ASSIGNMENT_STATE = CampaignRedisState("factory-dags-assignment")

        val MINIONS_ASSIGNMENT_STATE = CampaignRedisState("minions-assignment")

        val WARMUP_STATE = CampaignRedisState("warmup")

        val MINIONS_STARTUP_STATE = CampaignRedisState("minions-startup")

        val RUNNING_STATE = CampaignRedisState("running")

        val COMPLETION_STATE = CampaignRedisState("completion")

        val FAILURE_STATE = CampaignRedisState("failure")

        val ABORTING_STATE = CampaignRedisState("aborting")

        private val internalStates = ConcurrentHashMap<String, CampaignRedisState>(
        ).also {
            it.putAll(
                mapOf(
                    FACTORY_DAGS_ASSIGNMENT_STATE.name to FACTORY_DAGS_ASSIGNMENT_STATE,
                    MINIONS_ASSIGNMENT_STATE.name to MINIONS_ASSIGNMENT_STATE,
                    WARMUP_STATE.name to WARMUP_STATE,
                    MINIONS_STARTUP_STATE.name to MINIONS_STARTUP_STATE,
                    RUNNING_STATE.name to RUNNING_STATE,
                    COMPLETION_STATE.name to COMPLETION_STATE,
                    FAILURE_STATE.name to FAILURE_STATE,
                    ABORTING_STATE.name to ABORTING_STATE,
                )
            )
        }

        val states: Collection<CampaignRedisState>
            get() = internalStates.values

        fun register(state: CampaignRedisState) {
            internalStates[state.name] = state
        }

        fun valueOf(name: String): CampaignRedisState = internalStates[name]!!
    }

}