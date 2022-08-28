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

/**
 * Interface of a scenario to be configured.
 *
 * @author Eric Jessé
 */
interface ConfigurableScenarioSpecification : RetrySpecification {

    /**
     * Defines how the start of the minion should evolve in the scenario.
     */
    fun rampUp(specification: RampUpSpecification.() -> Unit)

    /**
     * Default number of minions. This value is multiplied by a runtime factor to provide the total number of minions on the scenario.
     */
    var minionsCount: Int
}