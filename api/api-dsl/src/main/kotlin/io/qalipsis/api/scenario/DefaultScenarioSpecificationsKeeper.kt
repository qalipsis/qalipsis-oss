package io.qalipsis.api.scenario

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Property
import io.qalipsis.api.context.ScenarioId
import jakarta.inject.Singleton
import java.util.Optional

/**
 * Default implementation of the [ScenarioSpecificationsKeeper].
 *
 * @author Eric Jessé
 */
@Singleton
internal class DefaultScenarioSpecificationsKeeper(
    @Property(name = "scenarios-selectors") scenariosSelectors: Optional<String>
) : ScenarioSpecificationsKeeper {

    private val exactMatchers = mutableSetOf<String>()
    private val patternMatchers = mutableSetOf<Regex>()

    init {
        if (scenariosSelectors.isPresent && scenariosSelectors.get().isNotBlank()) {
            scenariosSelectors.get().split(",").filter { it.isNotBlank() }.forEach {
                if (it.contains('*') || it.contains('?')) {
                    patternMatchers.add(Regex(it.replace('?', '.').replace("*", ".*")))
                } else {
                    exactMatchers.add(it.trim())
                }
            }
        }
    }

    override fun clear() {
        scenariosSpecifications.clear()
    }

    override fun asMap(): Map<ScenarioId, ConfiguredScenarioSpecification> {
        return filterScenarios(scenariosSpecifications)
    }

    /**
     * Filters out the scenarios that are not matching the existing selectors. When no selector is set, [scenarios] is returned.
     */
    @KTestable
    private fun filterScenarios(scenarios: Map<ScenarioId, ConfiguredScenarioSpecification>): Map<ScenarioId, ConfiguredScenarioSpecification> {
        return if (exactMatchers.isEmpty() && patternMatchers.isEmpty()) {
            scenarios
        } else scenarios.filterKeys { scenarioId ->
            exactMatchers.contains(scenarioId) || patternMatchers.any { pattern -> pattern.matches(scenarioId) }
        }
    }
}
