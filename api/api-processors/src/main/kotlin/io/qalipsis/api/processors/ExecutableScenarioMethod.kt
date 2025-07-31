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

package io.qalipsis.api.processors

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * Wrapping class to transport metadata about the scenario to declare.
 *
 * @property scenarioMethod annotated method declaring the scenario
 * @property scenarioClass class enclosing [scenarioMethod]
 * @property loaderClassName generated simple name for the class used to execute [scenarioMethod]
 * @property loaderFullClassName fully qualified name for the class used to execute [scenarioMethod]
 *
 * @author Eric Jessé
 */
data class ExecutableScenarioMethod(
    val scenarioMethod: ExecutableElement,
) {
    val scenarioClass = this.scenarioMethod.enclosingElement as TypeElement

    val loaderClassName: String = "${scenarioClass.simpleName}\$\$${this.scenarioMethod.simpleName}"

    val loaderFullClassName: String = "$LOADER_PREFIX.$loaderClassName"

    private companion object {

        const val LOADER_PREFIX = "io.qalipsis.api.scenariosloader"
    }
}