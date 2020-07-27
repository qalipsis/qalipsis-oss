package io.evolue.runtime

import io.evolue.core.cross.configuration.ENV_AUTOSTART
import io.evolue.core.cross.configuration.ENV_STANDALONE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
internal class EvolueStarterIntegrationTest {

    @Test
    @Timeout(4)
    internal fun `should start as default`() {
        val exitCode = Evolue.start(arrayOf())

        assertEquals(0, exitCode)
        assertTrue(Evolue.applicationContext!!.environment.activeNames.containsAll(
            listOf(ENV_STANDALONE, ENV_AUTOSTART, "config")))
    }

    @Test
    @Timeout(8)
    internal fun `should start with additional environments`() {
        val exitCode =
            Evolue.start(arrayOf("-e", "these", "-e", "are", "-e", "my", "-e", "additional", "-e", "environments"))

        assertEquals(0, exitCode)
        assertTrue(Evolue.applicationContext!!.environment.activeNames.containsAll(
            listOf(ENV_STANDALONE, ENV_AUTOSTART, "config", "these", "are", "my", "additional", "environments")))
    }
}
