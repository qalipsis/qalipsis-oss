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

package io.qalipsis.core.factory.orchestration

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.collections.concurrentTableOf
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.lifetime.ProcessExitCodeSupplier
import jakarta.inject.Singleton
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of the locally supported scenarios.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
class ScenarioRegistryImpl(
    @Property(name = "scenario.allow-empty", defaultValue = "false")
    private val allowEmptyScenario: Boolean
) : ScenarioRegistry, ProcessExitCodeSupplier {

    private val scenarios = ConcurrentHashMap<ScenarioName, Scenario>()

    private val dags = concurrentTableOf<ScenarioName, DirectedAcyclicGraphName, DirectedAcyclicGraph>()

    override fun contains(scenarioName: ScenarioName): Boolean {
        return scenarios.keys.contains(scenarioName)
    }

    override fun get(scenarioName: ScenarioName): Scenario? {
        return scenarios[scenarioName]
    }

    override fun get(scenarioName: ScenarioName, dagId: DirectedAcyclicGraphName): DirectedAcyclicGraph? {
        return dags.get(scenarioName, dagId)
    }

    override fun add(scenario: Scenario) {
        scenarios[scenario.name] = scenario
        scenario.dags.forEach {
            dags.put(scenario.name, it.name, it)
        }
    }

    override fun all() = scenarios.values

    override suspend fun await(): Optional<Int> {
        // Returns the code 2 when no scenario was found.
        return if (scenarios.isEmpty() && !allowEmptyScenario) {
            log.error { "No enabled scenario could be found in the classpath" }
            Optional.of(102)
        } else {
            Optional.empty()
        }
    }

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE

    private companion object {
        val log = logger()
    }

}