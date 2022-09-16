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

package io.qalipsis.core.redis

object RedisUtils {

    /**
     * Creates a convenient prefix for the Redis keys, considering the tenant.
     */
    @JvmStatic
    fun buildKeysPrefixForTenant(tenant: String): String {
        return if (tenant.isNotBlank()) {
            "$tenant:"
        } else {
            ""
        }
    }

    /**
     * Loads a LUA script from the classpath or returns an exception if it does not exist.
     */
    @JvmStatic
    fun loadScript(name: String): ByteArray {
        val resource =
            requireNotNull(this::class.java.getResourceAsStream(name)) { "Redis script $name cannot be found" }

        // Removes the blank lines and comments to optimize the compilation.
        return resource.bufferedReader(Charsets.UTF_8).readLines()
            .filterNot { it.isBlank() || it.trimStart().startsWith("--") }
            .joinToString("\n").encodeToByteArray()
    }
}