package io.qalipsis.runtime.bootstrap

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.nio.file.Files

/**
 * @author Eric Jessé
 */
@Timeout(60)
internal class QalipsisBootstrapWithExternalSourcesIntegrationTest {

    @BeforeEach
    internal fun setUp() {
        val currentDir = Files.createTempDirectory("test")
        System.setProperty("user.dir", currentDir.toFile().absolutePath)
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            """./config/qalipsis.properties,property.test=%s""",
            """./config/qalipsis.json,{"property":{"test":"%s"}}""",
            """./config/qalipsis.yml,property.test: %s""",
            """./config/qalipsis.yaml,property.test: %s""",
            """./qalipsis.properties,property.test=%s""",
            """./qalipsis.json,{"property":{"test":"%s"}}""",
            """./qalipsis.yml,property.test: %s""",
            """./qalipsis.yaml,property.test: %s""",
        ]
    )
    @Timeout(30)
    internal fun `should start with property value from external file`(path: String, content: String) {
        val rootDir = File(System.getProperty("user.dir"))
        val randomValue = "${Math.random() * 100000}"
        val configFile = File(rootDir, path)
        configFile.parentFile.mkdirs()
        configFile.writeText(String.format(content, randomValue))

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
        assertThat(
            qalipsisBootstrap.applicationContext.environment
                .getRequiredProperty("property.test", String::class.java)
        ).isEqualTo(randomValue)
    }

}
