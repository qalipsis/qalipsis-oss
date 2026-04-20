/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.processors.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.key
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ConfigurationOverrideReaderTest {

    private val reader = ConfigurationOverrideReader()

    @Test
    internal fun `should return empty overrides when file does not exist`(@TempDir tempDir: File) {
        val result = reader.read(tempDir.absolutePath)

        assertThat(result.properties).isEmpty()
        assertThat(result.groups).isEmpty()
    }

    @Test
    internal fun `should parse override with ignore`() {
        val yaml = """
            properties:
              factory.cache.ttl:
                ignore: true
        """.trimIndent()

        val result = reader.parse(yaml)

        assertThat(result.properties).hasSize(1)
        assertThat(result.properties).key("factory.cache.ttl").isNotNull().all {
            transform { it.ignore }.isTrue()
            transform { it.description }.isNull()
        }
    }

    @Test
    internal fun `should parse override with description`() {
        val yaml = """
            properties:
              factory.node-id:
                description: "Override description for node ID"
        """.trimIndent()

        val result = reader.parse(yaml)

        assertThat(result.properties).hasSize(1)
        assertThat(result.properties).key("factory.node-id").isNotNull().all {
            transform { it.ignore }.isFalse()
            transform { it.description }.isEqualTo("Override description for node ID")
        }
    }

    @Test
    internal fun `should parse extra entry with type and default`() {
        val yaml = """
            properties:
              custom.added-property:
                type: String
                default: "value"
                description: "Manually added property"
        """.trimIndent()

        val result = reader.parse(yaml)

        assertThat(result.properties).hasSize(1)
        assertThat(result.properties).key("custom.added-property").isNotNull().all {
            transform { it.ignore }.isFalse()
            transform { it.type }.isEqualTo("String")
            transform { it.default }.isEqualTo("value")
            transform { it.description }.isEqualTo("Manually added property")
        }
    }

    @Test
    internal fun `should parse multiple property entries`() {
        val yaml = """
            properties:
              factory.node-id:
                description: "Override description"
              factory.cache.ttl:
                ignore: true
              custom.added-property:
                type: String
                default: "value"
                description: "Manually added property"
        """.trimIndent()

        val result = reader.parse(yaml)

        assertThat(result.properties).hasSize(3)
    }

    @Test
    internal fun `should return empty overrides for empty YAML`() {
        val result = reader.parse("")

        assertThat(result.properties).isEmpty()
        assertThat(result.groups).isEmpty()
    }

    @Test
    internal fun `should return empty overrides for YAML without properties key`() {
        val yaml = """
            other:
              key: value
        """.trimIndent()

        val result = reader.parse(yaml)

        assertThat(result.properties).isEmpty()
    }

    @Test
    internal fun `should read from file`(@TempDir tempDir: File) {
        val overrideFile = File(tempDir, ConfigurationOverrideReader.OVERRIDE_FILE_NAME)
        overrideFile.writeText(
            """
            properties:
              factory.node-id:
                description: "From file"
            """.trimIndent()
        )

        val result = reader.read(tempDir.absolutePath)

        assertThat(result.properties).hasSize(1)
        assertThat(result.properties).key("factory.node-id").isNotNull().all {
            transform { it.description }.isEqualTo("From file")
        }
    }

    @Test
    internal fun `should parse groups`() {
        val yaml = """
            groups:
              - title: "Head Configuration"
                pattern: "head.**"
              - title: "Hazelcast"
                pattern: "hazelcast.**"
        """.trimIndent()

        val result = reader.parse(yaml)

        assertThat(result.groups).hasSize(2)
        assertThat(result.groups).index(0).all {
            transform { it.title }.isEqualTo("Head Configuration")
            transform { it.pattern }.isEqualTo("head.**")
        }
        assertThat(result.groups).index(1).all {
            transform { it.title }.isEqualTo("Hazelcast")
            transform { it.pattern }.isEqualTo("hazelcast.**")
        }
    }

    @Test
    internal fun `should skip groups with missing title or pattern`() {
        val yaml = """
            groups:
              - title: "Valid Group"
                pattern: "valid.**"
              - title: ""
                pattern: "empty-title.**"
              - title: "No Pattern"
              - pattern: "no-title.**"
        """.trimIndent()

        val result = reader.parse(yaml)

        assertThat(result.groups).hasSize(1)
        assertThat(result.groups).index(0).all {
            transform { it.title }.isEqualTo("Valid Group")
        }
    }

    @Test
    internal fun `should parse groups and properties together`() {
        val yaml = """
            groups:
              - title: "Factory"
                pattern: "factory.**"
            properties:
              factory.node-id:
                description: "Override"
        """.trimIndent()

        val result = reader.parse(yaml)

        assertThat(result.groups).hasSize(1)
        assertThat(result.properties).hasSize(1)
    }

    @Test
    internal fun `should match single-segment wildcard`() {
        val group = PropertyGroup("Test", "factory.*")

        assertThat(group.matches("factory.zone")).isTrue()
        assertThat(group.matches("factory.node-id")).isTrue()
        assertThat(group.matches("factory.cache.ttl")).isFalse()
        assertThat(group.matches("other.zone")).isFalse()
    }

    @Test
    internal fun `should match double-star wildcard`() {
        val group = PropertyGroup("Test", "factory.**")

        assertThat(group.matches("factory.zone")).isTrue()
        assertThat(group.matches("factory.cache.ttl")).isTrue()
        assertThat(group.matches("factory.campaign.configuration.max")).isTrue()
        assertThat(group.matches("other.zone")).isFalse()
    }

    @Test
    internal fun `should match pattern with fixed segments and wildcards`() {
        val group = PropertyGroup("Test", "hazelcast.kubernetes.*")

        assertThat(group.matches("hazelcast.kubernetes.namespace")).isTrue()
        assertThat(group.matches("hazelcast.kubernetes.pod-label-name")).isTrue()
        assertThat(group.matches("hazelcast.multicast.group")).isFalse()
        assertThat(group.matches("hazelcast.kubernetes.nested.deep")).isFalse()
    }

    @Test
    internal fun `should match question mark wildcard`() {
        val group = PropertyGroup("Test", "ab?d")

        assertThat(group.matches("abcd")).isTrue()
        assertThat(group.matches("abxd")).isTrue()
        assertThat(group.matches("abd")).isFalse()
        assertThat(group.matches("ab.d")).isFalse()
    }

    @Test
    internal fun `should match exact pattern without wildcards`() {
        val group = PropertyGroup("Test", "factory.zone")

        assertThat(group.matches("factory.zone")).isTrue()
        assertThat(group.matches("factory.zone.sub")).isFalse()
        assertThat(group.matches("factory.zon")).isFalse()
    }
}
