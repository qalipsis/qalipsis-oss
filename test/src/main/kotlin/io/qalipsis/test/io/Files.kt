package io.qalipsis.test.io

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


/**
 * Reads a file line by line.
 */
fun readFileLines(
    input: InputStream, removeComments: Boolean = false, commentsToken: String = "#",
    excludeEmptyLines: Boolean = true, trim: Boolean = true
): List<String> {
    return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
        val lines = mutableListOf<String>()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.let {
                var l = it
                if (removeComments) {
                    l = removeComments(l, commentsToken)
                }
                if (trim) {
                    l = l.trim()
                }
                if (l.isNotEmpty() || !excludeEmptyLines) {
                    lines.add(l)
                }
            }
        }
        lines
    }
}

private fun removeComments(line: String, commentsToken: String): String {
    val commentStart = line.indexOf(commentsToken)
    return if (commentStart >= 0) {
        line.substring(0, commentStart)
    } else {
        line
    }
}

/**
 * Reads a complete classpath resource.
 */
fun <T : Any> T.readResource(resourceName: String, charset: Charset = StandardCharsets.UTF_8) =
    this::class.java.classLoader.getResource(resourceName)!!.readText(charset)

/**
 * Reads a classpath resource line by line.
 */
fun <T : Any> T.readResourceLines(
    resourceName: String, removeComments: Boolean = false,
    commentsToken: String = "#", excludeEmptyLines: Boolean = true,
    trim: Boolean = true
) =
    readFileLines(
        this::class.java.classLoader.getResourceAsStream(resourceName)!!, removeComments, commentsToken,
        excludeEmptyLines, trim
    )
