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
