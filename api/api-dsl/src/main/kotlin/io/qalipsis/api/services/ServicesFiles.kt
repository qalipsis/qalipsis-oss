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

package io.qalipsis.api.services

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets


/**
 * A helper class for reading and writing Services files.
 *
 * Fetched from https://github.com/google/auto/blob/master/service/processor/src/main/java/com/google/auto/service/processor/ServicesFiles.java under Apache License, Version 2.0.
 */
object ServicesFiles {

    /**
     * Reads the set of services from a file.
     *
     * @param input not `null`. Closed after use.
     * @return a not `null Set` of services.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun readFile(input: InputStream): Set<String> {
        return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readLines()
            .map { line ->
                line.trim()
            }.map { line ->
                val commentStart = line.indexOf('#')
                if (commentStart >= 0) {
                    line.substring(0, commentStart)
                } else {
                    line
                }
            }.filterNot { line -> line.isBlank() }
            .toSet()
    }

    /**
     * Writes the set of services to a file.
     *
     * @param output not `null`
     * @param services a not `null Collection` of services.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun writeFile(services: Collection<String>, output: OutputStream) {
        BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
            for (service in services) {
                writer.write(service)
                writer.newLine()
            }
            writer.flush()
        }
    }
}
