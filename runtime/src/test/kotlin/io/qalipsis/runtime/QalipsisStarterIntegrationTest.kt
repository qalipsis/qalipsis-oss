package io.qalipsis.runtime

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.execute
import io.qalipsis.api.steps.returns
import io.qalipsis.core.cross.configuration.ENV_AUTOSTART
import io.qalipsis.core.cross.configuration.ENV_STANDALONE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
@Timeout(60)
internal class QalipsisStarterIntegrationTest {

    @Test
    @Timeout(20)
    internal fun `should start as default`() {
        val exitCode = Qalipsis.start(arrayOf("-s", "do-nothing-scenario"))

        assertEquals(0, exitCode)
        assertTrue(
            Qalipsis.applicationContext.environment.activeNames.containsAll(
                listOf(ENV_STANDALONE, ENV_AUTOSTART, "config")
            )
        )
    }

    @Test
    @Timeout(20)
    internal fun `should start with additional environments`() {
        val exitCode =
            Qalipsis.start(
                arrayOf(
                    "-s", "do-nothing-scenario", "-e", "these", "-e", "are", "-e", "my",
                    "-e", "additional", "-e", "environments"
                )
            )

        assertEquals(0, exitCode)
        assertThat(Qalipsis.applicationContext.environment.activeNames).containsAll(
            ENV_STANDALONE, ENV_AUTOSTART, "config", "these", "are", "my", "additional", "environments"
        )
    }

    @Test
    @Timeout(20)
    internal fun `should return an error when the scenario does not exist`() {
        val exitCode = Qalipsis.start(arrayOf("-s", "no-scenario"))

        assertEquals(1, exitCode)
    }

    @Test
    @Timeout(20)
    internal fun `should return an error when the scenario fails`() {
        val exitCode = Qalipsis.start(arrayOf("-s", "failing-scenario"))

        assertEquals(1, exitCode)
    }

    @Test
    @Timeout(20)
    internal fun `should start with property value`() {
        var exitCode = Qalipsis.start(arrayOf("-s", "do-nothing-scenario", "-c", "property.test=test-1"))
        assertEquals(0, exitCode)
        assertThat(Qalipsis.applicationContext.environment.getRequiredProperty("property.test", String::class.java))
            .isEqualTo("test-1")

        exitCode = Qalipsis.start(arrayOf("-s", "do-nothing-scenario", "-c", "property.test=test-2"))
        assertEquals(0, exitCode)
        assertThat(Qalipsis.applicationContext.environment.getRequiredProperty("property.test", String::class.java))
            .isEqualTo("test-2")
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
            .execute<Unit> { throw RuntimeException("There is an error") }
            .configure { report { reportErrors = true } }
            .blackHole()
    }

}
