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

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.services.ServicesFiles
import java.util.concurrent.Semaphore

/**
 * Class in charge of initializing the scenarios from the classpath.
 */
class ClasspathScenarioInitializer : ScenarioFactory {

    override fun newScenario(): ConfigurableScenarioSpecification {
        val scenarioSpec = ScenarioSpecificationImplementation(CURRENT_SCENARIO_NAME!!)
        CURRENT_SCENARIO_SPECIFICATION = scenarioSpec
        return scenarioSpec
    }

    companion object {

        /**
         * Sempahore preventing from scenarios specifications to be generated concurrently.
         */
        private val LOADING_SEMAPHORE = Semaphore(1)

        /**
         * Name of the currently being created scenario specification.
         */
        private var CURRENT_SCENARIO_NAME: String? = null

        /**
         * Lately created scenario specification.
         */
        private var CURRENT_SCENARIO_SPECIFICATION: ScenarioSpecificationImplementation? = null

        /**
         * Instances of all the [ScenarioLoader] present in the classpath.
         */
        private val scenariosLoader = this::class.java.classLoader.getResources("META-INF/services/qalipsis/scenarios")
            .toList()
            .asSequence()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { scenarioMetadata ->
                // Loading from the TSV form.
                // TODO Improve it using JSON.
                val loaderClass = scenarioMetadata.split("\t")[3]
                Class.forName(loaderClass).getConstructor().newInstance() as ScenarioLoader
            }
            .associateBy { it.name }

        /**
         * Recreates all the scenario specifications from the [ScenarioLoader] in the classpath.
         *
         * @param injector injection utility for beans and properties
         * @param scenariosSelectors selection patterns, as comma-separated wildcard strings
         */
        fun reload(
            injector: Injector,
            scenariosSelectors: String?
        ): Map<ScenarioName, ConfiguredScenarioSpecification> {
            val exactMatchers = mutableSetOf<String>()
            val patternMatchers = mutableSetOf<Regex>()

            // Creates the matchers from the scenario selectors.
            if (!scenariosSelectors.isNullOrBlank()) {
                scenariosSelectors.split(",").filter { it.isNotBlank() }.forEach {
                    if (it.contains('*') || it.contains('?')) {
                        patternMatchers.add(Regex(it.replace('?', '.').replace("*", ".*")))
                    } else {
                        exactMatchers.add(it.trim())
                    }
                }
            }

            // Filters the scenario loaders having names matching the selectors.
            val filteredLoaders = if (exactMatchers.isEmpty() && patternMatchers.isEmpty()) {
                scenariosLoader
            } else scenariosLoader.filterKeys { scenarioName ->
                exactMatchers.contains(scenarioName) || patternMatchers.any { pattern -> pattern.matches(scenarioName) }
            }

            // Executes the load on the scenario loaders one-by-one.
            val result = filteredLoaders.mapValues { (scenarioName, loader) ->
                LOADING_SEMAPHORE.acquire()
                // Set the name globally to make it available to the newScenario function.
                CURRENT_SCENARIO_NAME = scenarioName
                loader.load(injector)
                val scenarioSpecification = CURRENT_SCENARIO_SPECIFICATION!!
                LOADING_SEMAPHORE.release()

                scenarioSpecification.description = loader.description?.takeUnless(String::isNullOrBlank)
                scenarioSpecification.version = loader.version
                scenarioSpecification.builtAt = loader.builtAt

                scenarioSpecification
            }

            CURRENT_SCENARIO_NAME = null
            CURRENT_SCENARIO_SPECIFICATION = null

            return result
        }
    }
}
