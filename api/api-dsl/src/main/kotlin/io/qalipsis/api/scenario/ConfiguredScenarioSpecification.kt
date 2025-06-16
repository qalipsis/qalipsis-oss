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
