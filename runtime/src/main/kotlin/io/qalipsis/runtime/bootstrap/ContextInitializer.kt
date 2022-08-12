package io.qalipsis.runtime.bootstrap

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.env.Environment
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.processors.ServicesLoader
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.runtime.MicronautBootstrap
import java.io.File
import java.util.Properties

/**
 * Class in charge of initializing the [io.micronaut.context.ApplicationContext] for QALIPSIS.
 *
 * @author Eric Jessé
 */
internal class ContextInitializer(
    private val role: DeploymentRole,
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
        val properties = mutableMapOf<String, Any>()
        // Adds the build properties to the application context.
        properties.putAll(BUILD_PROPERTIES.mapKeys { "qalipsis.${it.key}" })

        addFileSystemConfigurationFiles()

        val environments = linkedSetOf<String>()
        when (role) {
            DeploymentRole.STANDALONE -> {
                configureStandalone(environments, properties)
            }
            DeploymentRole.HEAD -> {
                configureHead(environments)
            }
            DeploymentRole.FACTORY -> {
                configureFactory(environments, properties)
            }
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
            System.setProperty(Environment.PROPERTY_SOURCES_KEY, configurationFiles.joinToString())
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
        return if (role == DeploymentRole.STANDALONE || role == DeploymentRole.FACTORY) {
            val pluginsEnvironments = ServicesLoader.loadPlugins()
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
            print("${message} ")
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