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

/**
 * Generates an AsciiDoc table snippet from a list of [ConfigurationProperty] entries.
 * Supports optional [PropertyGroup]s that organize properties under titled sections
 * rendered as merged header rows.
 *
 * @author Eric Jessé
 */
internal class AsciidocTableGenerator {

    /**
     * Generates an AsciiDoc table string from the given properties, sorted alphabetically by path.
     * When [groups] is non-empty, properties are organized by group (in declaration order),
     * with each group preceded by a merged header row. Properties not matching any group
     * appear at the end without a header.
     */
    fun generate(properties: List<ConfigurationProperty>, groups: List<PropertyGroup> = emptyList()): String {
        val sb = StringBuilder()
        sb.appendLine("[cols=\"4,1,1,4\"]")
        sb.appendLine("|===")
        sb.appendLine("|Property |Type |Default |Description")

        if (groups.isEmpty()) {
            appendProperties(sb, properties.sortedBy { it.path })
        } else {
            val remaining = properties.toMutableList()

            for (group in groups) {
                val matched = remaining.filter { group.matches(it.path) }.sortedBy { it.path }
                remaining.removeAll(matched.toSet())

                if (matched.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine("4+h|${group.title}")
                    appendProperties(sb, matched)
                }
            }

            // Ungrouped properties at the end.
            if (remaining.isNotEmpty()) {
                appendProperties(sb, remaining.sortedBy { it.path })
            }
        }

        sb.appendLine()
        sb.appendLine("|===")
        return sb.toString()
    }

    private fun appendProperties(sb: StringBuilder, properties: List<ConfigurationProperty>) {
        for (prop in properties) {
            sb.appendLine()
            sb.appendLine("|`${prop.path}`")
            sb.appendLine("|`${prop.type}`")
            sb.appendLine("|${prop.defaultValue?.let { "`$it`" } ?: ""}")
            sb.appendLine("|${prop.description ?: ""}")
        }
    }
}
