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
internal data class ExecutableScenarioMethod(
    val scenarioMethod: ExecutableElement,
) {
    val scenarioClass = this.scenarioMethod.enclosingElement as TypeElement

    val loaderClassName: String = "${scenarioClass.simpleName}\$\$${this.scenarioMethod.simpleName}"

    val loaderFullClassName: String = "$LOADER_PREFIX.$loaderClassName"

    private companion object {

        const val LOADER_PREFIX = "io.qalipsis.api.scenariosloader"
    }
}