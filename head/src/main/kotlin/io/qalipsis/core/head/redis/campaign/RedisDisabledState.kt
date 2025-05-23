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

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.head.campaign.states.DisabledState

@ExperimentalLettuceCoroutinesApi
internal class RedisDisabledState(
    campaign: RunningCampaign,
    isSuccessful: Boolean = true,
    private val operations: CampaignRedisOperations
) : DisabledState(campaign, isSuccessful) {

    override suspend fun doInit(): List<Directive> {
        log.debug { "Initializing the status ${this::class.simpleName} for the campaign ${campaign.key}" }
        return try {
            super.doInit()
        } finally {
            operations.clean(campaign)
        }
    }

    private companion object {
        val log = logger()
    }
}