package io.qalipsis.runtime.deployments

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.startsWith
import io.qalipsis.runtime.Qalipsis
import io.qalipsis.runtime.bootstrap.QalipsisBootstrap
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.time.Instant

/**
 * Test class to validate the execution of QALIPSIS as a standalone application.
 *
 * @author Eric Jessé
 */
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
    internal fun `should create a process to start standalone and execute the scenario`() {
        // given
        val before = Instant.now()
        val qalipsisProcess = jvmProcessUtils.startNewJavaProcess(
            Qalipsis::class,
            arguments = arrayOf(
                "--autostart",
                "-s", "deployment-test",
                "-c", "report.export.junit.enabled=true",
                "-c", "report.export.junit.folder=build/test-results/standalone-deployment",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "-c", "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "-c", "logging.level.io.qalipsis.core.factory.init.FactoryInitializerImpl=TRACE"
            ),
            jvmOptions = arrayOf("-Xmx256m")
        )
        qalipsisProcess.await(Duration.ofSeconds(20))
        val after = Instant.now()

        // then
        assertSuccessfulExecution(qalipsisProcess, before, after)
    }

}