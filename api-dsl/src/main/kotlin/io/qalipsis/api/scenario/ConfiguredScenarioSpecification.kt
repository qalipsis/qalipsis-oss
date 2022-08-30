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

import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.retry.RetryPolicy

/**
 * Interface for an implementation of [ScenarioSpecification], on which the configuration can be read.
 *
 * @author Eric Jessé
 */
interface ConfiguredScenarioSpecification : StepSpecificationRegistry {

    /**
     * Default minions count to run in the tree under load when runtime factor is 1.
     */
    val minionsCount: Int

    /**
     * [ExecutionProfile] defining how the start of the minion should evolve in the scenario.
     */
    val executionProfile: ExecutionProfile?

    /**
     * Default [RetryPolicy] defined for all the steps of the scenario, when not otherwise specified.
     */
    val retryPolicy: RetryPolicy?

    val dagsCount: Int
}
