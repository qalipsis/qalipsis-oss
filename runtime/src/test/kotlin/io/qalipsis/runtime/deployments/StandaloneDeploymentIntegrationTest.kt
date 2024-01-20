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

package io.qalipsis.runtime.deployments

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.startsWith
import io.qalipsis.runtime.Qalipsis
import io.qalipsis.runtime.bootstrap.QalipsisBootstrap
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import java.time.Duration
import java.time.Instant

/**
 * Test class to validate the execution of QALIPSIS as a standalone application.
 *
 * @author Eric Jessé
 */
@DisabledIfEnvironmentVariable(
    named = "GITHUB_ACTIONS",
    matches = "true",
    disabledReason = "Does not work on Github Actions, to be investigated"
)
internal class StandaloneDeploymentIntegrationTest : AbstractDeploymentIntegrationTest() {

    @Test
    @Timeout(10)
    internal fun `should create a process to display the help and leave`() {
        // given
        val qalipsisProcess = jvmProcessUtils.startNewJavaProcess(
            Qalipsis::class,
            arguments = arrayOf("-h")
        )

        // when
        qalipsisProcess.await(Duration.ofSeconds(2))

        // then
        assertThat(qalipsisProcess.outputLines).any {
            it.startsWith("Usage: qalipsis [")
        }
    }

    @Test
    @Timeout(40)
    internal fun `should start standalone and execute the scenario`() {
        val exitCode = QalipsisBootstrap().start(
            arrayOf(
                "--autostart",
                "-s", "deployment-test",
                "-c", "report.export.console-live.enabled=false",
                "-c", "report.export.junit.enabled=true",
                "-c", "report.export.junit.folder=build/test-results/standalone-deployment",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "-c", "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "-c", "logging.level.io.qalipsis.core.factory.init.FactoryInitializerImpl=TRACE"
            )
        )
        Assertions.assertEquals(0, exitCode)
    }

    @Test
    @Timeout(40)
    internal fun `should start standalone and execute the scenario with failures`() {
        val exitCode = QalipsisBootstrap().start(
            arrayOf(
                "--autostart",
                "-s", "deployment-test-with-failures",
                "-c", "report.export.console-live.enabled=false",
                "-c", "report.export.junit.enabled=true",
                "-c", "report.export.junit.folder=build/test-results/standalone-deployment",
            )
        )
        Assertions.assertEquals(201, exitCode)
    }

    @Test
    @Timeout(40)
    internal fun `should create a process to start standalone and execute the scenario`() {
        // given
        val before = Instant.now()
        val qalipsisProcess = jvmProcessUtils.startNewJavaProcess(
            Qalipsis::class,
            arguments = arrayOf(
                "--autostart",
                "-s", "deployment-test",
                "-c", "report.export.console-live.enabled=false",
                "-c", "report.export.console.enabled=true",
                "-c", "report.export.junit.enabled=true",
                "-c", "report.export.junit.folder=build/test-results/standalone-deployment",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "-c", "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "-c", "logging.level.io.qalipsis.core.factory.init.FactoryInitializerImpl=TRACE"
            ),
            jvmOptions = arrayOf("-Xmx256m")
        )
        qalipsisProcess.await(Duration.ofSeconds(30))
        val after = Instant.now()

        // then
        assertSuccessfulExecution(qalipsisProcess, before, after)
    }

}