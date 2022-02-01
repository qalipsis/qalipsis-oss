package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.collections.concurrentTableOf
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of the locally supported scenarios.
 *
 * @author Eric Jessé
 */
@Singleton
internal class ScenarioRegistryImpl : ScenarioRegistry {

    private val scenarios = ConcurrentHashMap<ScenarioId, Scenario>()

    private val dags = concurrentTableOf<ScenarioId, DirectedAcyclicGraphId, DirectedAcyclicGraph>()

    override fun contains(scenarioId: ScenarioId): Boolean {
        return scenarios.keys.contains(scenarioId)
    }

    override fun get(scenarioId: ScenarioId): Scenario? {
        return scenarios[scenarioId]
    }

    override fun get(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): DirectedAcyclicGraph? {
        return dags.get(scenarioId, dagId)
    }

    override fun add(scenario: Scenario) {
        scenarios[scenario.id] = scenario
        scenario.dags.forEach {
            dags.put(scenario.id, it.id, it)
        }
    }

    override fun all() = scenarios.values
}