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

package io.qalipsis.core.head.campaign

import io.qalipsis.core.head.configuration.DefaultCampaignConfiguration

/**
 * Interface to define methods that apply certain constraints to a running campaign.
 *
 * @author Francisca Eze
 */
interface CampaignConstraintsProvider {

    /**
     * Supplies an instance of [DefaultCampaignConfiguration].
     *
     * @param tenant defines the tenant owning the campaign to which ti inject the configuration
     */
    suspend fun supply(tenant: String): DefaultCampaignConfiguration
}