package io.qalipsis.runtime.bootstrap

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.pipe
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.verify
import io.qalipsis.core.configuration.ExecutionEnvironments.AUTOSTART
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * @author Eric Jessé
 */
@Timeout(60)
internal class QalipsisBootstrapIntegrationTest {

    private val out = System.out

    @AfterEach
    internal fun tearDown() {
        System.setOut(out)
    }

    @Test
    @Timeout(5)
    internal fun `should display the help`() {
        val output = ByteArrayOutputStream()
        System.setOut(PrintStream(output))
        val qalipsisBootstrap = QalipsisBootstrap()
        val exitCode = qalipsisBootstrap.start(arrayOf("-h"))

        assertEquals(0, exitCode)
        assertThat(output.toString()).all {
            contains("Usage: qalipsis")
            contains("Load test software for monolithic and distributed systems")
        }
    }

    @Test
    @Timeout(5)
    internal fun `should display the version`() {
        val output = ByteArrayOutputStream()
        System.setOut(PrintStream(output))
        val qalipsisBootstrap = QalipsisBootstrap()
        val exitCode = qalipsisBootstrap.start(arrayOf("-V"))

        assertEquals(0, exitCode)
        assertThat(output.toString()).contains("QALIPSIS version ")
    }

    @Test
    @Timeout(30)
    internal fun `should start as default`() {
        val qalipsisBootstrap = QalipsisBootstrap()
        val exitCode = qalipsisBootstrap.start(
            arrayOf(
                "-a",
                "-s", "do-nothing-scenario",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "-c", "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "-c", "logging.level.io.qalipsis.core.factory.init.FactoryInitializerImpl=TRACE"
            )
        )

        assertEquals(0, exitCode)
        assertTrue(
            qalipsisBootstrap.applicationContext.environment.activeNames.containsAll(
                listOf(STANDALONE, AUTOSTART, "config")
            )
        )
    }

    @Test
    @Timeout(30)
    internal fun `should start with additional environments`() {
        val qalipsisBootstrap = QalipsisBootstrap()
        val exitCode =
            qalipsisBootstrap.start(
                arrayOf(
                    "-a",
                    "-s", "do-nothing-scenario",
                    "-e", "these", "-e", "are", "-e", "my", "-e", "additional", "-e", "environments",
                    "-c", "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                    "-c", "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                    "-c", "logging.level.io.qalipsis.core.head.campaign=TRACE",
                    "-c", "logging.level.io.qalipsis.core.factory.init.FactoryInitializerImpl=TRACE"
                )
            )

        assertEquals(0, exitCode)
        assertThat(qalipsisBootstrap.applicationContext.environment.activeNames).containsAll(
            STANDALONE, AUTOSTART, "config", "these", "are", "my", "additional", "environments"
        )
    }

    @Test
    @Timeout(20)
    internal fun `should return an error when the scenario does not exist`() {
        val exitCode = QalipsisBootstrap().start(arrayOf("-a", "-s", "no-scenario"))

        assertNotEquals(0, exitCode)
    }

    @Test
    @Timeout(20)
    internal fun `should return an error when the scenario fails`() {
        val exitCode = QalipsisBootstrap().start(arrayOf("-a", "-s", "failing-scenario"))

        assertNotEquals(0, exitCode)
    }

    @Test
    @Timeout(30)
    internal fun `should start with property value`() {
        val randomValue = "${Math.random() * 100000}"
        val qalipsisBootstrap = QalipsisBootstrap()
        val exitCode = qalipsisBootstrap.start(
            arrayOf(
                "-a",
                "-s", "do-nothing-scenario",
                "-c", "property.test=$randomValue",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "-c", "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "-c", "logging.level.io.qalipsis.core.factory.init.FactoryInitializerImpl=TRACE"
            )
        )
        assertEquals(0, exitCode)
        assertThat(
            qalipsisBootstrap.applicationContext.environment.getRequiredProperty(
                "property.test",
                String::class.java
            )
        )
            .isEqualTo(randomValue)
    }

    @Test
    @Timeout(20)
    internal fun `should exit properly on when a timeout occurs in the parent thread`() {
        // given
        val qalipsisBootstrap = QalipsisBootstrap()
        val thread = Thread {
            qalipsisBootstrap.start(arrayOf("-a", "-s", "do-nothing-scenario", "-c", "runtime.minimal-duration=5s"))
        }.apply { start() }
        Thread.sleep(2000)
        assertThat(qalipsisBootstrap.applicationContext.isRunning).isTrue()

        // when
        thread.interrupt()
        Thread.sleep(1000)

        // then
        assertThat(qalipsisBootstrap.applicationContext.isRunning).isFalse()
    }

    @Scenario
    fun doNothingScenario() {
        scenario("do-nothing-scenario") {
            minionsCount = 1
            rampUp { regular(1000, 1) }
        }.start()
            .returns(Unit)
            .blackHole()
    }

    @Scenario
    fun failingScenario() {
        scenario("failing-scenario") {
            minionsCount = 1
            rampUp { regular(1000, 1) }
        }.start()
            .pipe<Unit>()
            .verify { assertThat(true).isFalse() }
            .blackHole()
    }

}
