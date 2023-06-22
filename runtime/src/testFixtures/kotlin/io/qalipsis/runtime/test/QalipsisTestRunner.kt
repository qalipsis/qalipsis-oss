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

package io.qalipsis.runtime.test

import io.aerisconsulting.catadioptre.invokeInvisible

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

    override fun withConfiguration(vararg configuration: String): ConfigurableQalipsisTestRunner {
        return ConfigurableQalipsisTestRunnerImpl().withConfiguration(*configuration)
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
     * Command-line configuration to apply.
     */
    fun withConfiguration(vararg configuration: String): ConfigurableQalipsisTestRunner

    /**
     * Starts a local standalone qalipsis process with the test profile enabled and the passed arguments.
     */
    fun execute(vararg args: String): Int

}

internal class ConfigurableQalipsisTestRunnerImpl : ConfigurableQalipsisTestRunner {

    val scenarios = mutableListOf<String>()

    val environments = mutableListOf("config")

    val configuration = mutableListOf("report.export.console-live.enabled=false")

    override fun withScenarios(vararg scenarios: String): ConfigurableQalipsisTestRunner {
        this.scenarios.addAll(scenarios.toList())
        return this
    }

    override fun withEnvironments(vararg environments: String): ConfigurableQalipsisTestRunner {
        this.environments.addAll(environments.toList())
        return this
    }

    override fun withConfiguration(vararg configuration: String): ConfigurableQalipsisTestRunner {
        this.configuration.addAll(configuration)
        return this
    }

    override fun execute(vararg args: String): Int {
        val allArgs = mutableListOf("standalone", "--autostart")
        if (scenarios.isNotEmpty()) {
            allArgs += "-s"
            allArgs += scenarios.joinToString(separator = ",", transform = String::trim)
        }

        allArgs += "-e"
        allArgs += "test"
        environments.forEach {
            allArgs += "-e"
            allArgs += it.trim()
            // Also adds each env with the suffix -test.
            allArgs += "-e"
            allArgs += "${it.trim()}-test"
        }

        configuration.forEach {
            allArgs += "-c"
            allArgs += it.trim()
        }

        allArgs += args.toList()
        return bootstrapClassConstructor.newInstance().invokeInvisible("start", allArgs.toTypedArray())
    }

    private companion object {

        /**
         * Default constructor of [io.qalipsis.runtime.bootstrap.QalipsisBootstrap], not accessible outside the module.
         */
        val bootstrapClassConstructor =
            Class.forName("io.qalipsis.runtime.bootstrap.QalipsisBootstrap").constructors.first()

    }
}