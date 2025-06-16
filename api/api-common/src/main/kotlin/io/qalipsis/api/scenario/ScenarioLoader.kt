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

package io.qalipsis.api.scenario

import io.qalipsis.api.context.ScenarioName
import java.time.Instant

/**
 * Interface of a scenario provider.
 *
 * @property name unique identifier of the scenario
 * @property description display name or user-friendly description of the scenario
 * @property version version of the scenario, should be a dot-separated version
 * @property builtAt timestamp when the scenario was compiled
 *
 * @author Eric Jessé
 */
interface ScenarioLoader {

    val name: ScenarioName

    val description: String?
        get() = null

    val version: String

    val builtAt: Instant

    /**
     * Returns a creator of scenario, receiving the injector.
     */
    fun load(injector: Injector)

}