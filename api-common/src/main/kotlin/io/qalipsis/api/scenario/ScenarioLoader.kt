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