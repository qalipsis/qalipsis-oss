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

package io.qalipsis.api.events

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Singleton
import org.apache.commons.text.StringEscapeUtils
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import kotlin.reflect.KClass

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
        val level = event.level.toString().lowercase()
        val sb = StringBuilder("""{"@timestamp":$timestamp,"name":"$name","level":"$level"""")
        if (event.tags.isNotEmpty()) {
            event.tags.joinToString(",") { tag ->
                """"${StringEscapeUtils.escapeJson(tag.key)}":"${StringEscapeUtils.escapeJson(tag.value)}""""
            }.let {
                sb.append(""","tags":{$it}""")
            }

        }
        event.value?.let { addValue(it, sb) }

        sb.append("}")
        return sb.toString()
    }

    @Suppress("kotlin:S3776")
    private fun addValue(value: Any, sb: StringBuilder) {
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

                // The first value of each supported type is added independently.
                val valuesToGroup = mutableListOf<Any>()
                groupNonNullValuesByFieldName(actualValues).forEach { (field, values) ->
                    if (field.isBlank()) {
                        valuesToGroup.addAll(values)
                    } else {
                        addValue(values.first(), sb)
                        if (values.size > 1) {
                            valuesToGroup.addAll(values.subList(1, values.size))
                        }
                    }
                }

                if (valuesToGroup.isNotEmpty()) {
                    sb.append(""","values":[""")
                        .append(valuesToGroup.joinToString(",") { jsonMapper.writeValueAsString(it.toString()) })
                        .append(']')
                }
            }

            else -> {
                // Convert to JSON and copy the JSON as a raw value.
                val stringRepresentation = jsonMapper.writeValueAsString(value)
                sb.append(""","value":""").append(jsonMapper.writeValueAsString(stringRepresentation))
            }
        }
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

    private fun groupNonNullValuesByFieldName(values: Iterable<*>) =
        values.filterNotNull().groupBy { typeByFieldName[it::class] ?: "" }

    companion object {

        private val typeByFieldName: Map<KClass<*>, String> = mutableMapOf(
            String::class to "message",
            Boolean::class to "boolean",
            Double::class to "number",
            Long::class to "number",
            Int::class to "number",
            Float::class to "number",
            Short::class to "number",
            Byte::class to "number",
            Instant::class to "date",
            ZonedDateTime::class to "date",
            LocalDateTime::class to "date",
            Throwable::class to "error",
            Duration::class to "duration",
            EventGeoPoint::class to "point",
            Throwable::class to "error",
            EventRange::class to "numeric-range"
        )

        @JvmStatic
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }

}
