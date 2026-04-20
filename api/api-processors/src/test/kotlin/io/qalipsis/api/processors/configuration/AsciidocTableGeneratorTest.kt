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

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

internal class AsciidocTableGeneratorTest {

    private val generator = AsciidocTableGenerator()

    @Test
    internal fun `should generate empty table when no properties`() {
        val result = generator.generate(emptyList())

        assertThat(result).isEqualTo(
            """
            |[cols="4,1,1,4"]
            ||===
            ||Property |Type |Default |Description
            |
            ||===
            |""".trimMargin()
        )
    }

    @Test
    internal fun `should generate table with properties sorted alphabetically`() {
        val properties = listOf(
            ConfigurationProperty("factory.zone", "String", null, "Zone of the factory."),
            ConfigurationProperty("factory.cache.ttl", "Duration", "1m", "Time to live for cache entries."),
            ConfigurationProperty(
                "factory.assignment.evaluation-batch-size",
                "Int",
                "10",
                "Size of the evaluation batches of minions to assign."
            )
        )

        val result = generator.generate(properties)

        assertThat(result).contains("|`factory.assignment.evaluation-batch-size`")
        assertThat(result).contains("|`factory.cache.ttl`")
        assertThat(result).contains("|`factory.zone`")

        // Verify order: assignment < cache < zone.
        val assignmentIndex = result.indexOf("factory.assignment")
        val cacheIndex = result.indexOf("factory.cache")
        val zoneIndex = result.indexOf("factory.zone")
        assertThat(assignmentIndex < cacheIndex && cacheIndex < zoneIndex).isEqualTo(true)
    }

    @Test
    internal fun `should render default value in backticks when present`() {
        val properties = listOf(
            ConfigurationProperty("my.prop", "Int", "42", "A property.")
        )

        val result = generator.generate(properties)

        assertThat(result).contains("|`42`")
    }

    @Test
    internal fun `should render empty default when null`() {
        val properties = listOf(
            ConfigurationProperty("my.prop", "String", null, "A property.")
        )

        val result = generator.generate(properties)

        // The default column should be empty (just "|" followed by newline).
        assertThat(result).contains("|`String`\n|\n|A property.")
    }

    @Test
    internal fun `should render empty description when null`() {
        val properties = listOf(
            ConfigurationProperty("my.prop", "String", "val", null)
        )

        val result = generator.generate(properties)

        assertThat(result).contains("|`val`\n|\n")
    }

    @Test
    internal fun `should render groups with merged header rows`() {
        val properties = listOf(
            ConfigurationProperty("factory.zone", "String", null, "Zone."),
            ConfigurationProperty("factory.cache.ttl", "Duration", "1m", "TTL."),
            ConfigurationProperty("head.heartbeat-delay", "Duration", "30s", "Heartbeat."),
            ConfigurationProperty("other.prop", "String", null, "Other.")
        )
        val groups = listOf(
            PropertyGroup("Factory Settings", "factory.**"),
            PropertyGroup("Head Settings", "head.**")
        )

        val result = generator.generate(properties, groups)

        // Group headers should be present.
        assertThat(result).contains("4+h|Factory Settings")
        assertThat(result).contains("4+h|Head Settings")

        // Factory properties should appear after their header.
        val factoryHeaderIndex = result.indexOf("4+h|Factory Settings")
        val cacheIndex = result.indexOf("factory.cache.ttl")
        val zoneIndex = result.indexOf("factory.zone")
        assertThat(factoryHeaderIndex < cacheIndex).isTrue()
        assertThat(factoryHeaderIndex < zoneIndex).isTrue()

        // Head properties after the Head header.
        val headHeaderIndex = result.indexOf("4+h|Head Settings")
        val heartbeatIndex = result.indexOf("head.heartbeat-delay")
        assertThat(headHeaderIndex < heartbeatIndex).isTrue()

        // Ungrouped properties should appear (without a group header).
        assertThat(result).contains("|`other.prop`")
    }

    @Test
    internal fun `should sort properties within each group`() {
        val properties = listOf(
            ConfigurationProperty("factory.zone", "String", null, null),
            ConfigurationProperty("factory.assignment.batch", "Int", "10", null),
            ConfigurationProperty("factory.cache.ttl", "Duration", "1m", null)
        )
        val groups = listOf(PropertyGroup("Factory", "factory.**"))

        val result = generator.generate(properties, groups)

        val assignmentIndex = result.indexOf("factory.assignment.batch")
        val cacheIndex = result.indexOf("factory.cache.ttl")
        val zoneIndex = result.indexOf("factory.zone")
        assertThat(assignmentIndex < cacheIndex && cacheIndex < zoneIndex).isTrue()
    }

    @Test
    internal fun `should skip empty groups`() {
        val properties = listOf(
            ConfigurationProperty("factory.zone", "String", null, null)
        )
        val groups = listOf(
            PropertyGroup("Empty Group", "nothing.**"),
            PropertyGroup("Factory", "factory.**")
        )

        val result = generator.generate(properties, groups)

        assertThat(result).doesNotContain("4+h|Empty Group")
        assertThat(result).contains("4+h|Factory")
        assertThat(result).contains("|`factory.zone`")
    }

    @Test
    internal fun `should place ungrouped properties at the end`() {
        val properties = listOf(
            ConfigurationProperty("other.first", "String", null, null),
            ConfigurationProperty("factory.zone", "String", null, null),
            ConfigurationProperty("other.second", "String", null, null)
        )
        val groups = listOf(PropertyGroup("Factory", "factory.*"))

        val result = generator.generate(properties, groups)

        val factoryIndex = result.indexOf("factory.zone")
        val otherFirstIndex = result.indexOf("other.first")
        val otherSecondIndex = result.indexOf("other.second")
        assertThat(factoryIndex < otherFirstIndex).isTrue()
        assertThat(factoryIndex < otherSecondIndex).isTrue()
    }

    @Test
    internal fun `should behave as before when no groups are provided`() {
        val properties = listOf(
            ConfigurationProperty("b.prop", "String", null, null),
            ConfigurationProperty("a.prop", "String", null, null)
        )

        val result = generator.generate(properties, emptyList())

        assertThat(result).doesNotContain("4+h|")
        val aIndex = result.indexOf("a.prop")
        val bIndex = result.indexOf("b.prop")
        assertThat(aIndex < bIndex).isTrue()
    }
}
