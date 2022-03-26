package io.qalipsis.runtime.bootstrap

import io.micronaut.context.ApplicationContext
import io.qalipsis.runtime.bootstrap.DeploymentRole.STANDALONE
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable

/**
 * Configurable bootstrap of QALIPSIS runtime.
 * It uses the library Picocli to parse the command-line.
 *
 * @author Eric Jessé
 */
@Command(
    name = "qalipsis",
    description = ["Load test software for monolithic and distributed systems"],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProviderWithVariables::class
)
internal class QalipsisBootstrap : Callable<Unit> {

    @Option(
        names = ["-p", "--prompt"], description = ["Prompts for campaign configuration."],
        defaultValue = "false"
    )
    private var prompt: Boolean = false

    @Option(
        names = ["-a", "--autostart"],
        description = ["Automatically start a new campaign, requires the relevant configuration."],
        defaultValue = "false"
    )
    private var autostart: Boolean = false

    @Option(
        names = ["--persistent"],
        description = ["Keep the execution history and cluster state in memory only - applicable in standalone and head mode only."],
        defaultValue = "false"
    )
    private var persistent: Boolean = false

    @Option(
        names = ["-e", "--environments"],
        description = ["Environments to enable further configuration."]
    )
    private var environments: Array<String> = arrayOf()

    @Option(
        names = ["-s", "--scenarios"],
        description = ["Comma-separated list of scenarios to include, wildcard such as * or ? are supported, defaults to all the scenarios."]
    )
    private var scenariosSelectors: String = ""

    @Option(names = ["-c", "--configuration"], description = ["Configure the execution (parameter=value)."])
    private var configuration: Array<String> = arrayOf()

    @Parameters(
        description = [
            "Role of the started process:",
            "   - standalone (default)",
            "   - head",
            "   - factory"
        ], defaultValue = "standalone", completionCandidates = DeploymentRoleAutoCompletion::class, arity = "0..1"
    )
    private var role: DeploymentRole = STANDALONE

    internal lateinit var applicationContext: ApplicationContext

    private var exitCode = 0

    /**
     * Starts the bootstrap of Qalipsis.
     */
    fun start(args: Array<String> = emptyArray()): Int {

        val runtimeExitCode = CommandLine(this)
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setUsageHelpAutoWidth(true)
            .execute(*args)
        return exitCode.takeIf { it != 0 } ?: runtimeExitCode
    }

    override fun call() {
        val contextInitializer = ContextInitializer(
            role = role,
            commandLineEnvironments = environments,
            commandLineConfiguration = configuration,
            scenariosSelectors = scenariosSelectors,
            prompt = prompt,
            autostart = autostart,
            persistent = persistent
        )
        val applicationContextBuilder = contextInitializer.build()
        val appContext = QalipsisApplicationContext(role)

        exitCode = try {
            // Refer to Micronaut.start() for reference.
            // We use the direct builder of the application context here in order to better manage the
            // components to start (server or not) and when/how the application should exit.

            applicationContext = applicationContextBuilder.start()
            applicationContext.use {
                appContext.execute(it)
            }
        } finally {
            appContext.shutdown()
        }
    }
}