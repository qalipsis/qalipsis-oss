package io.qalipsis.core.factories.orchestration

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.Scenario
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

@Singleton
class ScenariosRegistryImpl : ScenariosRegistry {

    private val scenarios = ConcurrentHashMap<ScenarioId, Scenario>()

    override fun contains(scenarioId: ScenarioId): Boolean {
        return scenarios.keys.contains(scenarioId)
    }

    override fun get(scenarioId: ScenarioId): Scenario? {
        return scenarios[scenarioId]
    }

    override fun add(scenario: Scenario) {
        scenarios[scenario.id] = scenario
    }
}