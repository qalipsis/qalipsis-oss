package io.qalipsis.runtime

import io.qalipsis.core.cross.configuration.ENV_AUTOSTART
import io.qalipsis.core.cross.configuration.ENV_STANDALONE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
internal class QalipsisStarterIntegrationTest {

    @Test
    @Timeout(20)
    internal fun `should start as default`() {
        val exitCode = Qalipsis.start(arrayOf())

        assertEquals(0, exitCode)
        assertTrue(Qalipsis.applicationContext!!.environment.activeNames.containsAll(
            listOf(ENV_STANDALONE, ENV_AUTOSTART, "config")))
    }

    @Test
    @Timeout(20)
    internal fun `should start with additional environments`() {
        val exitCode =
            Qalipsis.start(arrayOf("-e", "these", "-e", "are", "-e", "my", "-e", "additional", "-e", "environments"))

        assertEquals(0, exitCode)
        assertTrue(Qalipsis.applicationContext!!.environment.activeNames.containsAll(
            listOf(ENV_STANDALONE, ENV_AUTOSTART, "config", "these", "are", "my", "additional", "environments")))
    }
}
