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

package io.qalipsis.core.head.factory

import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.jdbc.entity.FactoryEntity

/**
 * Service in charge of updating the details of a scenario in the persistent layer.
 */
interface ScenarioDetailsUpdater {

    /**
     * Creates, updates and deletes scenario-specific entities with accordance to scenarios received from an external source.
     */
    suspend fun saveOrUpdateScenarios(
        tenantReference: String,
        registrationScenarios: List<ScenarioSummary>,
        existingFactory: FactoryEntity,
        deleteAbsentScenarios: Boolean = true
    )

}