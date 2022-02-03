package io.qalipsis.runtime

import assertk.assertThat
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
        val exitCode = Qalipsis.start(
            arrayOf(
                "-s", "do-nothing-scenario",
                "-c", "logging.level.io.qalipsis.core.factory.inmemory.InMemoryMinionAssignmentKeeper=TRACE",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration=TRACE",
                "-c", "logging.level.io.qalipsis.core.head.campaign.AbstractCampaignManager=TRACE",
                "-c", "logging.level.io.qalipsis.core.inmemory.InMemoryFeedbackChannel=TRACE",
                "-c", "logging.level.io.qalipsis.api.messaging.AbstractLinkedSlotsBasedTopic=TRACE"
            )
        )

        assertEquals(0, exitCode)
        assertTrue(
            Qalipsis.applicationContext.environment.activeNames.containsAll(
                listOf(STANDALONE, AUTOSTART, "config")
            )
        )
    }

    @Test
    @Timeout(20)
    internal fun `should start with additional environments`() {
        val exitCode =
            Qalipsis.start(
                arrayOf(
                    "-s", "do-nothing-scenario",
                    "-e", "these", "-e", "are", "-e", "my", "-e", "additional", "-e", "environments",
                    "-c", "logging.level.io.qalipsis.core.factory.inmemory.InMemoryMinionAssignmentKeeper=TRACE",
                    "-c", "logging.level.io.qalipsis.core.factory.orchestration=TRACE",
                    "-c", "logging.level.io.qalipsis.core.head.campaign.AbstractCampaignManager=TRACE",
                    "-c", "logging.level.io.qalipsis.core.inmemory.InMemoryFeedbackChannel=TRACE",
                    "-c", "logging.level.io.qalipsis.api.messaging.AbstractLinkedSlotsBasedTopic=TRACE"
                )
            )

        assertEquals(0, exitCode)
        assertThat(Qalipsis.applicationContext.environment.activeNames).containsAll(
            STANDALONE, AUTOSTART, "config", "these", "are", "my", "additional", "environments"
        )
    }

    @Test
    @Timeout(20)
    internal fun `should return an error when the scenario does not exist`() {
        val exitCode = Qalipsis.start(arrayOf("-s", "no-scenario"))

        assertNotEquals(0, exitCode)
    }

    @Test
    @Timeout(20)
    internal fun `should return an error when the scenario fails`() {
        val exitCode = Qalipsis.start(arrayOf("-s", "failing-scenario"))

        assertNotEquals(0, exitCode)
    }

    @Test
    @Timeout(20)
    internal fun `should start with property value`() {
        val randomValue = "${Math.random() * 100000}"
        val exitCode = Qalipsis.start(
            arrayOf(
                "-s", "do-nothing-scenario",
                "-c", "property.test=$randomValue",
                "-c", "logging.level.io.qalipsis.core.factory.inmemory.InMemoryMinionAssignmentKeeper=TRACE",
                "-c", "logging.level.io.qalipsis.core.factory.orchestration=TRACE",
                "-c", "logging.level.io.qalipsis.core.head.campaign.AbstractCampaignManager=TRACE",
                "-c", "logging.level.io.qalipsis.core.inmemory.InMemoryFeedbackChannel=TRACE",
                "-c", "logging.level.io.qalipsis.api.messaging.AbstractLinkedSlotsBasedTopic=TRACE"
            )
        )
        assertEquals(0, exitCode)
        assertThat(Qalipsis.applicationContext.environment.getRequiredProperty("property.test", String::class.java))
            .isEqualTo(randomValue)
    }

    @Test
    @Timeout(20)
    internal fun `should exit properly on when a timeout occurs in the parent thread`() {
        // given
        val thread = Thread {
            Qalipsis.start(arrayOf("-s", "do-nothing-scenario", "-c", "runtime.minimal-duration=5s"))
        }.apply { start() }
        Thread.sleep(2000)
        assertThat(Qalipsis.applicationContext.isRunning).isTrue()

        // when
        thread.interrupt()
        Thread.sleep(1000)

        // then
        assertThat(Qalipsis.applicationContext.isRunning).isFalse()
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
