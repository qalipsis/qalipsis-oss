package io.qalipsis.api.events

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.util.StringEscapeUtils
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import javax.inject.Singleton

/**
 * Implementation of [EventConverter] to generate a JSON string.
 *
 * @author Eric Jessé
 */
@Singleton
class EventJsonConverter : EventConverter<String> {

    private val jsonMapper = ObjectMapper()

    /**
     * Generates a JSON representation of an event.
     *
     * Any type can be used for value, but [Boolean]s, [Number]s, [String]s, [java.time.Duration]s, [java.time.temporal.Temporal]s,
     * [EventGeoPoint]s, [EventRange]s and [Throwable]s are interpreted.
     *
     * [Iterable]s and [Array]s are converted to a JSON array of strings.
     */
    override fun convert(event: Event): String {
        val timestamp = event.timestamp.truncatedTo(ChronoUnit.MILLIS).toEpochMilli()
        val name = StringEscapeUtils.escapeJson(event.name)
        val level = event.level.toString().toLowerCase()
        val sb = StringBuilder("""{"@timestamp":$timestamp,"name":"$name","level":"$level"""")
        if (event.tags.isNotEmpty()) {
            event.tags.joinToString(",") { tag ->
                """"${StringEscapeUtils.escapeJson(tag.key)}":"${StringEscapeUtils.escapeJson(tag.value)}""""
            }.let {
                sb.append(""","tags":{$it}""")
            }

        }

        event.value?.let { value ->
            when {
                value is String -> {
                    sb.append(""","message":"""").append(StringEscapeUtils.escapeJson(value)).append('"')
                }
                value is Boolean -> {
                    sb.append(""","boolean":""").append(value)
                }
                value is Double && !value.isFinite() -> {
                    sb.append(""","non-finite-decimal":"""").append(value.toString()).append('"')
                }
                value is Number -> {
                    sb.append(""","number":""").append(value)
                }
                value is Instant || value is ZonedDateTime -> {
                    sb.append(""","date":"""").append(TIMESTAMP_FORMATTER.format(value as TemporalAccessor))
                        .append('"')
                }
                value is LocalDateTime -> {
                    sb.append(""","date":"""")
                        .append(TIMESTAMP_FORMATTER.format(value.atZone(ZoneId.systemDefault()))).append('"')
                }
                value is Throwable -> {
                    sb.append(""","error":"""").append(StringEscapeUtils.escapeJson(value.message)).append('"')
                        .append(""","stack-trace":"""").append(StringEscapeUtils.escapeJson(stackTraceToString(value)))
                        .append('"')
                }
                value is Duration -> {
                    sb.append(""","duration":${value.seconds}.${value.nano}""")
                }
                value is EventGeoPoint -> {
                    sb.append(""","point":""").append("[${value.longitude},${value.latitude}]")
                }
                value is EventRange<*> -> {
                    val lowerOperator = if (value.includeLower) "gte" else "gt"
                    val upperOperator = if (value.includeUpper) "lte" else "lt"
                    sb.append(""","numeric-range":{""").append(""""$lowerOperator":""").append(value.lowerBound)
                        .append(""","$upperOperator":""").append(value.upperBound).append("}")
                }
                value is Iterable<*> || value is Array<*> -> {
                    val actualValues = if (value is Iterable<*>) value else (value as Array<*>).toList()
                    sb.append(""","values":[""")
                        .append(actualValues.joinToString(",") { jsonMapper.writeValueAsString(it.toString()) })
                        .append(']')
                }
                else -> {
                    // Convert to JSON and copy the JSON as a raw value.
                    val stringRepresentation = jsonMapper.writeValueAsString(value)
                    sb.append(""","value":""").append(jsonMapper.writeValueAsString(stringRepresentation))
                }
            }
        }
        sb.append("}")
        return sb.toString()
    }

    /**
     * Converts the stack trace of a [Throwable] into a [String].
     */
    private fun stackTraceToString(throwable: Throwable): String {
        try {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    throwable.printStackTrace(pw)
                    return sw.toString()
                }
            }
        } catch (ioe: IOException) {
            throw IllegalStateException(ioe)
        }
    }

    companion object {

        @JvmStatic
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }

}
