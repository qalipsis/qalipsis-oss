package io.qalipsis.runtime.test

import io.qalipsis.runtime.Qalipsis

/**
 * Utils to execute scenarios in a concrete runtime.
 *
 * @author Eric Jessé
 */
object QalipsisTestRunner : ConfigurableQalipsisTestRunner {

    override fun withScenarios(vararg scenarios: String): ConfigurableQalipsisTestRunner {
        return ConfigurableQalipsisTestRunnerImpl().withScenarios(*scenarios)
    }

    override fun withEnvironments(vararg environments: String): ConfigurableQalipsisTestRunner {
        return ConfigurableQalipsisTestRunnerImpl().withEnvironments(*environments)
    }

    override fun execute(vararg args: String) = ConfigurableQalipsisTestRunnerImpl().execute(*args)

}

/**
 * Runner to execute scenarios in a concrete runtime.
 */
interface ConfigurableQalipsisTestRunner {

    /**
     * Restricts the executed scenarios to the list passed as parameter.
     * It support wildcards such as * and ?.
     */
    fun withScenarios(vararg scenarios: String): ConfigurableQalipsisTestRunner

    /**
     * Enables the environments for the execution.
     */
    fun withEnvironments(vararg environments: String): ConfigurableQalipsisTestRunner

    /**
     * Starts a local standalone qalipsis process with the test profile enabled and the passed arguments.
     */
    fun execute(vararg args: String): Int

}

internal class ConfigurableQalipsisTestRunnerImpl : ConfigurableQalipsisTestRunner {

    val scenarios = mutableListOf<String>()

    val environments = mutableListOf("config")

    override fun withScenarios(vararg scenarios: String): ConfigurableQalipsisTestRunner {
        this.scenarios.addAll(scenarios.toList())
        return this
    }

    override fun withEnvironments(vararg environments: String): ConfigurableQalipsisTestRunner {
        this.environments.addAll(environments.toList())
        return this
    }

    override fun execute(vararg args: String): Int {
        val allArgs = mutableListOf<String>()
        if (scenarios.isNotEmpty()) {
            allArgs.add("-s")
            allArgs.add(scenarios.joinToString(separator = ",", transform = String::trim))
        }

        allArgs.add("-e")
        allArgs.add("test")
        environments.forEach {
            allArgs.add("-e")
            allArgs.add(it.trim())
            // Also adds each env with the suffix -test.
            allArgs.add("-e")
            allArgs.add("${it.trim()}-test")
        }
        allArgs.addAll(args.toList())

        return Qalipsis.start(allArgs.toTypedArray())
    }

}