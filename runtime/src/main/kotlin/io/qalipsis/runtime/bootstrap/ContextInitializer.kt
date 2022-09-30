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

package io.qalipsis.runtime.bootstrap

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.env.Environment
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.services.ServicesFiles
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.lifetime.FactoryStartupComponent
import io.qalipsis.core.lifetime.HeadStartupComponent
import io.qalipsis.runtime.MicronautBootstrap
import io.qalipsis.runtime.bootstrap.DeploymentRole.FACTORY
import io.qalipsis.runtime.bootstrap.DeploymentRole.HEAD
import io.qalipsis.runtime.bootstrap.DeploymentRole.STANDALONE
import java.io.File
import java.util.Properties

/**
 * Class in charge of initializing the [io.micronaut.context.ApplicationContext] for QALIPSIS.
 *
 * @author Eric Jessé
 */
internal class ContextInitializer(
    var role: DeploymentRole,
    private val commandLineEnvironments: Array<String>,
    private val commandLineConfiguration: Array<String>,
    private val scenariosSelectors: String,
    private val prompt: Boolean,
    private val autostart: Boolean,
    private val persistent: Boolean
) {

    /**
     * Creates and configures a [ApplicationContextBuilder] considering the different arguments received by
     * the program at startup.
     */
    fun build(): ApplicationContextBuilder {
        validateAndDetectRole()

        val properties = mutableMapOf<String, Any>()
        // Adds the build properties to the application context.
        properties.putAll(BUILD_PROPERTIES.mapKeys { "qalipsis.${it.key}" })

        addFileSystemConfigurationFiles()

        val environments = linkedSetOf<String>()
        when (role) {
            HEAD -> configureHead(environments)
            FACTORY -> configureFactory(environments, properties)
            else -> configureStandalone(environments, properties)

        }
        environments.add("$role".lowercase())

        // The user can create a application-config.yml file to overload the default.
        environments.add("config")

        return ApplicationContext.builder()
            .banner(true)
            .environments(*environments.toTypedArray() + this.commandLineEnvironments)
            .packages("io.qalipsis")
            .mainClass(MicronautBootstrap::class.java)
            .properties(properties)
            .deduceEnvironment(false)
            .args(*(commandLineConfiguration.map { "--$it" }.toTypedArray()))
    }

    /**
     * Verifies that the required dependencies are in the classpath and detect the role when in AUTO mode.
     */
    private fun validateAndDetectRole() {
        val hasFactoryClasses = kotlin.runCatching { FactoryStartupComponent::class }.getOrNull() != null
        val hasHeadClasses = kotlin.runCatching { HeadStartupComponent::class }.getOrNull() != null

        require(hasFactoryClasses || hasHeadClasses) { "Neither head nor factory libraries found in the classpath!" }

        when (role) {
            STANDALONE -> {
                require(hasHeadClasses) { "The head library is missing in the classpath!" }
                require(hasFactoryClasses) { "The factory library is missing in the classpath!" }
            }
            HEAD -> require(hasHeadClasses) { "The head library is missing in the classpath!" }
            FACTORY -> require(hasFactoryClasses) { "The factory library is missing in the classpath!" }
            else -> role = when {
                hasHeadClasses && hasFactoryClasses -> STANDALONE
                hasHeadClasses -> HEAD
                else -> FACTORY
            }.also {
                log.info { "Detected role based upon the available libraries: $it" }
            }
        }
    }

    /**
     * Verifies the existence of the configuration files in the file system, and make them accessible to the
     * application context.
     *
     * See (Micronaut documentation)(https://docs.micronaut.io/latest/guide/#propertySource).
     */
    private fun addFileSystemConfigurationFiles() {
        val configurationFiles = mutableListOf<String>()
        CONFIGURATION_ROOTS.forEach { root ->
            val rootFile = File(System.getProperty("user.dir"), root)
            CONFIGURATION_EXTENSIONS.forEach { ext ->
                val configFile = File(rootFile, "${CONFIGURATION_FILE_NAME}.$ext")
                if (configFile.exists() && configFile.canRead()) {
                    configurationFiles += configFile.absolutePath
                }
            }
        }

        if (configurationFiles.isNotEmpty()) {
            log.info { "Loading the additional configuration from ${configurationFiles.joinToString()}" }
            System.setProperty(Environment.PROPERTY_SOURCES_KEY, configurationFiles.joinToString())
        } else {
            log.info { "No additional external configuration to load" }
        }
    }

    /**
     * Configures the context when the process has to be deployed as a standalone process, which includes
     * a head and a factory.
     */
    private fun configureStandalone(
        environments: MutableCollection<String>,
        properties: MutableMap<String, Any>
    ) {
        environments += loadEnvironmentsFromPlugins()
        log.info { "Starting QALIPSIS in standalone mode" }
        if (prompt) {
            if (!autostart) {
                environments.add(ExecutionEnvironments.AUTOSTART)
            }
            promptForConfiguration(properties)
        }
        // Do not run in server mode, exit as soon as the operations are complete.
        environments.add(Environment.CLI)

        if (autostart) {
            // Start the campaigns when everything is ready.
            environments.add(ExecutionEnvironments.AUTOSTART)
        }
        if (persistent) {
            environments.add(ExecutionEnvironments.POSTGRESQL)
        } else {
            environments.add(ExecutionEnvironments.TRANSIENT)
        }
        filterEnabledScenarios(properties)
    }

    /**
     * Configures the context when the process has to be deployed as a head.
     */
    private fun configureHead(environments: LinkedHashSet<String>) {
        environments.add(ExecutionEnvironments.REDIS)
        if (autostart) {
            // Start the campaigns when everything is ready.
            environments.add(ExecutionEnvironments.AUTOSTART)
        }
        if (persistent) {
            environments.add(ExecutionEnvironments.POSTGRESQL)
        } else {
            environments.add(ExecutionEnvironments.TRANSIENT)
        }
    }

    /**
     * Configures the context when the process has to be deployed as a factory.
     */
    private fun configureFactory(
        environments: LinkedHashSet<String>,
        properties: MutableMap<String, Any>
    ) {
        environments += loadEnvironmentsFromPlugins()
        environments.add(ExecutionEnvironments.REDIS)
        filterEnabledScenarios(properties)
    }

    /**
     * Loads all the environments required by the QALIPSIS plugins present in the classpath.
     */
    private fun loadEnvironmentsFromPlugins(): Collection<String> {
        return if (role == STANDALONE || role == FACTORY) {
            val pluginsEnvironments = loadPlugins()
            if (pluginsEnvironments.isNotEmpty()) {
                log.info { "Loading the plugins ${pluginsEnvironments.joinToString()}" }
            } else {
                log.info { "No plugin was found" }
            }
            pluginsEnvironments
        } else {
            emptySet()
        }
    }

    /**
     * Loads the profiles defined in the plugins.
     */
    private fun loadPlugins(): Collection<String> {
        return this.javaClass.classLoader.getResources("META-INF/services/qalipsis/plugin")
            .asSequence()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Adds the definition of the selectors for scenarios to load.
     * Others are just ignored, as if they were not in the classpath.
     */
    private fun filterEnabledScenarios(properties: MutableMap<String, Any>) {
        if (scenariosSelectors.isNotBlank()) {
            properties["scenarios-selectors"] = scenariosSelectors
        }
    }

    /**
     * Prompts all the configuration parameters to start a campaign.
     */
    private fun promptForConfiguration(properties: MutableMap<String, Any>) {
        promptAndValidate(
            "Enter the name of your campaign or leave blank for default:",
            "A value is mandatory",
            { this.trim() },
            { true }
        ).apply {
            if (this.isNotBlank()) {
                properties["campaign.key"] = this
            }
        }
        val loadSelectionStrategy: Int = promptAndValidate(
            "Do you want to enter [1] a minion count by scenario or [2] a minion count multiplier?",
            "Please select 1 or 2",
            { this.toInt() },
            { it == 1 || it == 2 }
        )
        if (loadSelectionStrategy == 1) {
            promptAndValidate(
                "Enter the expected number of minions by scenario:",
                "Please enter a positive integer value",
                { this.toInt() },
                { it > 0 }
            ).apply {
                properties["campaign.minions-count-per-scenario"] = this
            }
        } else {
            promptAndValidate(
                "Enter the minions count multiplier (< 1 will reduce the default, > 1 will augment the default):",
                "Please enter a positive decimal value",
                { this.toDouble() },
                { it > 0 }
            ).apply {
                properties["campaign.minions-factor"] = this
            }
        }
        promptAndValidate(
            "Enter the ramp-up speed multiplier (< 1 will slow down, > 1 will accelerate):",
            "Please enter a positive decimal value",
            { this.toDouble() },
            { it > 0 }
        ).apply {
            properties["campaign.speed-factor"] = this
        }
    }

    /**
     * Prompts a value to enter and validate the input.
     * When the input is not valid, it is prompted again.
     */
    private fun <T> promptAndValidate(
        message: String, errorMessage: String, conversion: String.() -> T,
        validation: (T) -> Boolean
    ): T {
        var choice: T? = null
        while (choice == null) {
            print("$message ")
            try {
                choice = readLine()?.conversion()
                if (choice == null || !validation(choice)) {
                    println("ERROR: ${errorMessage}")
                    choice = null
                }
            } catch (e: Exception) {
                println("ERROR: ${errorMessage}")
            }
        }
        return choice
    }

    private companion object {

        /**
         * Name of the configuration file.
         */
        const val CONFIGURATION_FILE_NAME = "qalipsis"

        /**
         * List of root folders where to look for configuration files.
         */
        val CONFIGURATION_ROOTS = listOf("./config", ".")

        /**
         * List of extensions for configuration files.
         */
        val CONFIGURATION_EXTENSIONS = listOf("properties", "json", "yml", "yaml")

        val BUILD_PROPERTIES = Properties().apply {
            load(ContextInitializer::class.java.getResourceAsStream("/build.properties"))
        }

        val log = logger()
    }

}