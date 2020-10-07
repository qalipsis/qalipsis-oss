package io.qalipsis.test.io

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Read a file line by line.
 */
fun readFile(input: InputStream, removeComments: Boolean = false, commentsToken: String = "#"): List<String> {
    return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
        val lines = mutableListOf<String>()
        var line: String?
        while (reader.readLine().also { line = it } != null) {

            if (removeComments) {
                val commentStart = line!!.indexOf(commentsToken)
                if (commentStart >= 0) {
                    line = line!!.substring(0, commentStart)
                }
            }
            line = line!!.trim()
            if (line!!.isNotEmpty()) {
                lines.add(line!!)
            }
        }
        lines
    }
}