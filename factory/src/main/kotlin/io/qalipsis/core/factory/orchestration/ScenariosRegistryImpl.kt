package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.Scenario
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

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