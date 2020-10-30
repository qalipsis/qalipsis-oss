package io.qalipsis.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.Micronaut
import io.qalipsis.api.factories.StartupFactoryComponent
import io.qalipsis.api.heads.StartupHeadComponent
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.cross.configuration.ENV_AUTOSTART
import io.qalipsis.core.cross.configuration.ENV_STANDALONE
import io.qalipsis.core.cross.configuration.ENV_VOLATILE
import io.qalipsis.core.heads.lifetime.ProcessBlocker
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.IVersionProvider
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable
import kotlin.system.exitProcess


/**
 * Starter object to launch the application Qalipsis as a JVM process.
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
        names = ["-p", "--prompt"], description = ["Prompt for campaign configuration"],
        defaultValue = "false"
    )
    var prompt: Boolean = false

    @Option(names = ["-e", "--environments"], description = ["Environments to enable further configuration"])
    var environments: Array<String> = arrayOf()

    @Option(
        names = ["-s", "--scenarios"],
        description = ["Comma-separated list of scenarios to include, wildcard such as * or ? are supported, default to all the scenarios"]
    )
    var scenariosSelectors: String = ""

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

    internal var applicationContext: ApplicationContext? = null

    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(start(args))
    }

    fun start(args: Array<String> = emptyArray()): Int {
        this.args = args
        return CommandLine(Qalipsis)
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setUsageHelpAutoWidth(true)
            .execute(*args)
    }

    enum class Role {
        STANDALONE // Runs head and factory side by side in the same JVM.
    }

    override fun call() {
        val environments = mutableListOf<String>()
        val properties = mutableMapOf<String, Any>()
        if (role == Role.STANDALONE) {
            if (prompt) {
                promptForConfiguration(properties)
            }

            if (scenariosSelectors.isNotBlank()) {
                properties["scenarios-selectors"] = scenariosSelectors
            }

            // Start the campaigns when everything is ready.
            environments.add(ENV_AUTOSTART)
            // Do not persist configuration of factories and scenarios.
            environments.add(ENV_VOLATILE)
            // Start head and one factory in the same process.
            environments.add(ENV_STANDALONE)
            // Do not run in server mode, exit as soon as the operations are complete.
            environments.add(Environment.CLI)
        }
        // The user can create a application-config.yml file to overload the default.
        environments.add("config")

        applicationContext = Micronaut.build()
            .environments(*environments.toTypedArray() + this.environments)
            .packages("io.qalipsis")
            .mainClass(MicronautBootstrap::class.java)
            .properties(properties)
            .deduceEnvironment(false)
            .args(*this.args)
            .start()

        // Force the loading if key services.
        if (role == Role.STANDALONE) {
            applicationContext!!.getBeansOfType(StartupFactoryComponent::class.java)
            applicationContext!!.getBeansOfType(StartupHeadComponent::class.java)
        }
        val processBlockers: Collection<ProcessBlocker> =
            applicationContext!!.getBeansOfType(ProcessBlocker::class.java)
        if (processBlockers.isNotEmpty()) {
            runBlocking {
                log.info("${processBlockers.size} service(s) to join before exiting the process")
                processBlockers.forEach { it.join() }
            }
        } else {
            log.info("There is no service to join before exiting the process")
        }
    }

    private fun promptForConfiguration(properties: MutableMap<String, Any>) {
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
            return arrayOf("\${COMMAND-FULL-NAME} version 0.1")
        }
    }

}
