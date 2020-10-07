package io.qalipsis.api.processors

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
internal object ServicesFiles {

    /**
     * Reads the set of services from a file.
     *
     * @param input not `null`. Closed after use.
     * @return a not `null Set` of services.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun readFile(input: InputStream): Set<String> {
        return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            val services = mutableSetOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val commentStart = line!!.indexOf('#')
                if (commentStart >= 0) {
                    line = line!!.substring(0, commentStart)
                }
                line = line!!.trim()
                if (line!!.isNotEmpty()) {
                    services.add(line!!)
                }
            }
            services
        }
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