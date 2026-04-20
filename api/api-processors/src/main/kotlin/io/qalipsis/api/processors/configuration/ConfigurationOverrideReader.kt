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

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Reads the optional `configuration-reference.yml` file to allow per-property overrides
 * such as ignoring properties, overriding descriptions, adding extra entries, and defining groups.
 *
 * @author Eric Jessé
 */
internal class ConfigurationOverrideReader {

    /**
     * Reads the override file and returns [ConfigurationOverrides].
     * Returns empty overrides if the file does not exist.
     */
    fun read(resourcesDir: String): ConfigurationOverrides {
        val file = File(resourcesDir, OVERRIDE_FILE_NAME)
        if (!file.exists()) {
            return ConfigurationOverrides()
        }
        return parse(file.readText())
    }

    /**
     * Parses the YAML content and returns [ConfigurationOverrides].
     */
    internal fun parse(yamlContent: String): ConfigurationOverrides {
        val yaml = Yaml()
        val root = yaml.load<Map<String, Any>>(yamlContent) ?: return ConfigurationOverrides()

        @Suppress("UNCHECKED_CAST")
        val properties = (root["properties"] as? Map<String, Map<String, Any>>)
            ?.mapValues { (_, attrs) ->
                PropertyOverride(
                    ignore = attrs["ignore"] as? Boolean ?: false,
                    description = attrs["description"] as? String,
                    type = attrs["type"] as? String,
                    default = attrs["default"]?.toString()
                )
            } ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        val groups = (root["groups"] as? List<Map<String, Any>>)
            ?.map { attrs ->
                PropertyGroup(
                    title = attrs["title"] as? String ?: "",
                    pattern = attrs["pattern"] as? String ?: ""
                )
            }
            ?.filter { it.title.isNotEmpty() && it.pattern.isNotEmpty() }
            ?: emptyList()

        return ConfigurationOverrides(properties = properties, groups = groups)
    }

    companion object {
        const val OVERRIDE_FILE_NAME = "configuration-reference.yml"
    }
}

/**
 * All overrides and groups parsed from the override YAML file.
 */
internal data class ConfigurationOverrides(
    val properties: Map<String, PropertyOverride> = emptyMap(),
    val groups: List<PropertyGroup> = emptyList()
)

/**
 * Override directives for a single configuration property.
 */
internal data class PropertyOverride(
    val ignore: Boolean = false,
    val description: String? = null,
    val type: String? = null,
    val default: String? = null
)

/**
 * A group of properties defined by a title and an Ant-style pattern.
 * The title is rendered as a merged header row in the AsciiDoc table.
 *
 * Ant pattern rules applied to dot-separated property paths:
 * - `*` matches any characters within a single segment (between dots)
 * - `**` matches any number of segments (including zero)
 * - `?` matches a single character
 *
 * Examples:
 * - `factory.*` matches `factory.zone` but not `factory.cache.ttl`
 * - `factory.**` matches `factory.zone` and `factory.cache.ttl`
 * - `hazelcast.kubernetes.*` matches `hazelcast.kubernetes.namespace`
 */
internal data class PropertyGroup(
    val title: String,
    val pattern: String
) {

    private val regex: Regex = antPatternToRegex(pattern)

    /**
     * Returns `true` if the given property path matches this group's Ant pattern.
     */
    fun matches(propertyPath: String): Boolean = regex.matches(propertyPath)

    companion object {

        /**
         * Converts an Ant-style pattern to a [Regex] for matching dot-separated property paths.
         */
        fun antPatternToRegex(pattern: String): Regex {
            val sb = StringBuilder("^")
            var i = 0
            while (i < pattern.length) {
                when {
                    pattern[i] == '*' && i + 1 < pattern.length && pattern[i + 1] == '*' -> {
                        // ** matches any number of segments (including zero).
                        sb.append(".*")
                        i += 2
                        // Skip a trailing dot after ** (e.g. "**.foo" -> the dot is the separator).
                        if (i < pattern.length && pattern[i] == '.') i++
                    }

                    pattern[i] == '*' -> {
                        // * matches within a single segment (no dots).
                        sb.append("[^.]*")
                        i++
                    }

                    pattern[i] == '?' -> {
                        sb.append("[^.]")
                        i++
                    }

                    pattern[i] == '.' -> {
                        sb.append("\\.")
                        i++
                    }

                    else -> {
                        sb.append(Regex.escape(pattern[i].toString()))
                        i++
                    }
                }
            }
            sb.append("$")
            return Regex(sb.toString())
        }
    }
}
