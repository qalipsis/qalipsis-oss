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

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.delay
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
import java.time.Duration

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
                    "-c", "logging.level.io.qalipsis.core.factory.init.FactoryInitializerImpl=TRACE",
                    "-c", "logging.level.io.qalipsis.runtime.bootstrap=TRACE"
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
            profile { regular(1000, 1) }
        }.start()
            .returns(Unit)
            .blackHole()
    }

    @Scenario
    fun failingScenario() {
        scenario("failing-scenario") {
            minionsCount = 1
            profile { regular(1000, 1) }
        }.start()
            .pipe<Unit>()
            .verify { assertThat(true).isFalse() }
            .blackHole()
    }

    @Scenario
    fun longRunningScenario() {
        scenario("long-running-scenario") {
            minionsCount = 1
            profile { regular(1000, 1) }
        }.start()
            .pipe<Unit>()
            .delay(Duration.ofSeconds(30))
            .blackHole()
    }
}
