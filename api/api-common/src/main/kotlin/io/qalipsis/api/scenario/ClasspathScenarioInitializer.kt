/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.scenario

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.scenario.ClasspathScenarioInitializer.Companion.SCENARIOS_DIR
import java.io.File
import java.net.JarURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Class in charge of initializing the scenarios from the classpath.
 *
 * Scenarios are discovered by scanning all JSON files under [SCENARIOS_DIR] in the classpath.
 * Each file is named after the fully qualified loader class (compatible with [Class.forName]),
 * with inner classes using `$` as separator instead of `.`.
 */
class ClasspathScenarioInitializer : ScenarioFactory {

    override fun newScenario(): ConfigurableScenarioSpecification {
        val scenarioSpec = ScenarioSpecificationImplementation(CURRENT_SCENARIO_NAME!!)
        CURRENT_SCENARIO_SPECIFICATION = scenarioSpec
        val callers = StackWalker.getInstance().walk { frames -> frames.iterator().asSequence().map { it.className }.toList() }
        findScenarioDeclaringPackage(callers)?.let(SCENARIOS_PACKAGES::add)
        return scenarioSpec
    }

    companion object {

        private const val SCENARIOS_DIR = "META-INF/services/qalipsis/scenarios"

        private const val SCENARIOS_LOADERS_PACKAGE_PREFIX = "io.qalipsis.api.scenariosloader."

        private val SCENARIOS_PACKAGES: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /**
         * Packages of the classes declaring the scenarios loaded so far.
         */
        val scenariosPackages: Set<String>
            get() = SCENARIOS_PACKAGES

        /**
         * Searches the package of the class declaring the scenario, as the caller of the generated [ScenarioLoader].
         *
         * @param callers names of the classes in the current call stack, the innermost first
         */
        internal fun findScenarioDeclaringPackage(callers: List<String>): String? {
            val loaderIndex = callers.indexOfFirst { it.startsWith(SCENARIOS_LOADERS_PACKAGE_PREFIX) }
            return callers.getOrNull(loaderIndex - 1)?.takeIf { loaderIndex > 0 }
                ?.substringBeforeLast('.', "")
                ?.takeIf(String::isNotEmpty)
        }

        /**
         * Semaphore preventing from scenarios specifications to be generated concurrently.
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
        private val scenariosLoader: Map<String, ScenarioLoader> = loadScenariosFromClasspath()

        /**
         * Scans the classpath for individual scenario JSON files and instantiates the
         * corresponding [ScenarioLoader] classes identified by the file names.
         */
        private fun loadScenariosFromClasspath(): Map<String, ScenarioLoader> {
            return this::class.java.classLoader.getResources(SCENARIOS_DIR)
                .toList()
                .asSequence()
                .flatMap { url -> listScenarioClassNames(url) }
                .map { className ->
                    Class.forName(className).getConstructor().newInstance() as ScenarioLoader
                }
                .associateBy { it.name }
        }

        /**
         * Lists the scenario loader class names found at the given classpath [URL],
         * supporting both file-system directories and JAR entries.
         */
        private fun listScenarioClassNames(url: URL): Sequence<String> {
            return when (url.protocol) {
                "file" -> {
                    val dir = File(url.toURI())
                    dir.listFiles { file -> file.name.endsWith(".json") }
                        ?.asSequence()
                        ?.map { it.name.removeSuffix(".json") }
                        ?: emptySequence()
                }

                "jar" -> {
                    val connection = url.openConnection() as JarURLConnection
                    val prefix = connection.entryName + "/"
                    connection.jarFile.entries().asSequence()
                        .filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".json") }
                        .map { it.name.removePrefix(prefix).removeSuffix(".json") }
                        .toList()
                        .asSequence()
                }

                else -> emptySequence()
            }
        }

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
