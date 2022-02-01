package io.qalipsis.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.EmbeddedApplication
import io.qalipsis.api.coroutines.CoroutineScopeProvider
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.processors.ServicesLoader
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.AutostartCampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.lifetime.FactoryStartupComponent
import io.qalipsis.core.lifetime.HeadStartupComponent
import io.qalipsis.core.lifetime.ProcessBlocker
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.IVersionProvider
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.Optional
import java.util.Properties
import java.util.concurrent.Callable
import kotlin.system.exitProcess

/**
 * Starter object to launch the application QALIPSIS as a JVM process.
 *
 * @author Eric Jessé
 */
@Command(
    name = "qalipsis",
    description = ["Load test software for monolithic and distributed systems"],
    mixinStandardHelpOptions = true,
    versionProvider = Qalipsis.VersionProviderWithVariables::class
)
object Qalipsis : Callable<Unit> {

    @Option(
        names = ["-p", "--prompt"], description = ["Prompt for campaign configuration."],
        defaultValue = "false"
    )
    var prompt: Boolean = false

    @Option(names = ["-e", "--environments"], description = ["Environments to enable further configuration."])
    var environments: Array<String> = arrayOf()

    @Option(
        names = ["-s", "--scenarios"],
        description = ["Comma-separated list of scenarios to include, wildcard such as * or ? are supported, defaults to all the scenarios."]
    )
    var scenariosSelectors: String = ""

    @Option(names = ["-c", "--configuration"], description = ["Command-line configuration."])
    var configuration: Array<String> = arrayOf()

    @Parameters(
        description = [
            "Role of the started process:",
            "   - standalone (default)",
            "   - head",
            "   - factory"
        ], defaultValue = "standalone", arity = "0..1"
    )
    var role: Role = Role.STANDALONE

    private lateinit var args: Array<String>

    @JvmStatic
    private val log = logger()

    internal lateinit var applicationContext: ApplicationContext

    private var exitCode = 0

    private val executionProperties = Properties()

    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(start(args))
    }

    fun start(args: Array<String> = emptyArray()): Int {
        executionProperties.load(Qalipsis::class.java.getResourceAsStream("/build.properties"))
        this.args = args
        val runtimeExitCode = CommandLine(Qalipsis)
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setUsageHelpAutoWidth(true)
            .execute(*args)
        return exitCode.takeIf { it != 0 } ?: runtimeExitCode
    }

    enum class Role {
        STANDALONE // Runs head and factory side by side in the same JVM.
    }

    override fun call() {
        val properties = mutableMapOf<String, Any>()
        // Adds the build properties to the application context.
        executionProperties.forEach { (key, value) ->
            properties["qalipsis.${key}"] = value
        }

        val environments = mutableListOf<String>()
        // Loads the profiles from the plugins.
        val pluginsProfiles = ServicesLoader.loadPlugins()
        log.info { "Loading the plugins ${pluginsProfiles.joinToString()}" }
        environments.addAll(pluginsProfiles)
        if (role == Role.STANDALONE) {
            if (prompt) {
                promptForConfiguration(properties)
            }

            if (scenariosSelectors.isNotBlank()) {
                properties["scenarios-selectors"] = scenariosSelectors
            }

            // Start the campaigns when everything is ready.
            environments.add(ExecutionEnvironments.AUTOSTART)
            // Do not persist configuration of factories and scenarios.
            environments.add(ExecutionEnvironments.VOLATILE)
            // Start head and one factory in the same process.
            environments.add(ExecutionEnvironments.STANDALONE)
            // Do not run in server mode, exit as soon as the operations are complete.
            environments.add(Environment.CLI)
        } else {
            // Enables the configuration to use Redis when not running as standalone.
            environments.add(ExecutionEnvironments.REDIS)
        }

        // The user can create a application-config.yml file to overload the default.
        environments.add("config")
        var embeddedApplication = Optional.empty<EmbeddedApplication<*>>()
        try {
            ApplicationContext.builder()
                .banner(true)
                .environments(*environments.toTypedArray() + this.environments)
                .packages("io.qalipsis")
                .mainClass(MicronautBootstrap::class.java)
                .properties(properties)
                .deduceEnvironment(false)
                .args(*(configuration.map { "--$it" }.toTypedArray()))
                .start().use {
                    applicationContext = it
                    embeddedApplication = applicationContext.findBean(EmbeddedApplication::class.java)
                    val processBlockers: Collection<ProcessBlocker> =
                        applicationContext.getBeansOfType(ProcessBlocker::class.java)
                            .sortedBy(ProcessBlocker::getOrder)
                    try {
                        doExecute(environments, processBlockers)
                    } catch (e: Exception) {
                        if (exitCode == 0) {
                            exitCode = 2
                        }
                        log.error(e) { e.message }
                        log.warn { "Cancelling all the process blockers" }
                        processBlockers.forEach { blocker ->
                            runCatching {
                                blocker.cancel()
                                applicationContext.destroyBean(blocker)
                            }
                        }
                        log.warn { "Forcing all the coroutine scope providers to close" }
                        applicationContext.getBeansOfType(CoroutineScopeProvider::class.java).forEach {
                            runCatching {
                                applicationContext.destroyBean(it)
                            }
                        }
                    }
                }
        } finally {
            tryAndLogOrNull(log) {
                embeddedApplication.ifPresent { embedded ->
                    if (embedded.isRunning) {
                        embedded.stop()
                    }
                }
            }
        }
    }

    private fun doExecute(environments: MutableList<String>, processBlockers: Collection<ProcessBlocker>) {
        // Forces the loading of key services.
        if (role == Role.STANDALONE) {
            startHead()
            startFactory()
        }

        if (processBlockers.isNotEmpty()) {
            runBlocking {
                log.info { "${processBlockers.size} service(s) to wait before exiting the process: ${processBlockers.joinToString { blocker -> "${blocker::class.simpleName}" }}" }
                processBlockers.forEach { blocker ->
                    log.debug { "Waiting for ${blocker::class.simpleName} to complete..." }
                    blocker.join()
                }
            }
        }

        if (ExecutionEnvironments.AUTOSTART in environments) {
            // Publishes the result just before leaving and set the exit code.
            applicationContext.findBean(CampaignReportStateKeeper::class.java)
                .ifPresent { campaignStateKeeper ->
                    applicationContext.findBean(AutostartCampaignConfiguration::class.java).ifPresent { campaign ->
                        exitCode = runBlocking {
                            val reportStatus = campaignStateKeeper.report(campaign.id).status
                            log.info { "Exiting the scenario with the status $reportStatus" }
                            reportStatus
                        }.exitCode
                    }
                }
        } else {
            log.info { "There is no service to await before exiting the process" }
        }
    }

    private fun startHead() {
        log.trace { "Triggering startup components for the head..." }
        val headerStarters = applicationContext.getBeansOfType(HeadStartupComponent::class.java)
            .sortedBy(HeadStartupComponent::getStartupOrder)
        for (starter in headerStarters) {
            log.trace { "Triggering ${starter::class.simpleName}" }
            starter.init()
            log.trace { "Triggered ${starter::class.simpleName}" }
        }
    }

    private fun startFactory() {
        log.trace { "Triggering startup components for the factory..." }
        val factoryStarters = applicationContext.getBeansOfType(FactoryStartupComponent::class.java)
            .sortedBy(FactoryStartupComponent::getStartupOrder)
        for (starter in factoryStarters) {
            log.trace { "Triggering ${starter::class.simpleName}" }
            starter.init()
            log.trace { "Triggered ${starter::class.simpleName}" }
        }
    }


    private fun promptForConfiguration(properties: MutableMap<String, Any>) {
        promptAndValidate(
            "Enter the name of your campaign or leave blank for default:",
            "A value is mandatory",
            { this.trim() },
            { true }
        ).apply {
            if (this.isNotBlank()) {
                properties["campaign.name"] = this
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

    internal class VersionProviderWithVariables : IVersionProvider {
        override fun getVersion(): Array<String> {
            return arrayOf("QALIPSIS version ${executionProperties.getProperty("version")}")
        }
    }

}
