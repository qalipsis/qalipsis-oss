package io.evolue.core.factory.events.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.annotations.VisibleForTesting
import io.evolue.api.events.EventGeoPoint
import io.evolue.api.events.EventRange
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.time.durationSinceNanos
import io.evolue.core.factory.events.Event
import io.evolue.core.factory.eventslogger.BufferedEventLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.http
import io.ktor.client.features.Charsets
import io.ktor.client.features.UserAgent
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.util.StringEscapeUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.Random
import java.util.UUID
import java.util.regex.Pattern
import javax.annotation.Nullable


/**
 * Implementation of [io.evolue.api.events.EventLogger] for Elasticsearch.
 * Inspired by [io.micrometer.elastic.ElasticMeterRegistry].
 *
 * @author Eric Jessé
 */
internal class ElasticsearchEventLogger(
    private val configuration: ElasticsearchEventLoggerConfiguration,
    private val meterRegistry: MeterRegistry,
    @Nullable providedHttpClient: HttpClient? = null
) : BufferedEventLogger(configuration.loggableLevel, configuration.lingerPeriod, configuration.batchSize) {

    private val indexFormatter = DateTimeFormatter.ofPattern(configuration.indexDatePattern)

    private val jsonMapper: ObjectMapper = ObjectMapper()

    private val random = Random()

    private val urls = configuration.urls.map { u -> if (u.endsWith("/")) "$u" else "$u/" }

    private val publicationContext =
        newFixedThreadPoolContext(configuration.publishers, "Elasticsearch-Events-Publisher")

    private val httpClient = providedHttpClient ?: HttpClient(Apache) {
        install(UserAgent) {
            agent = "evolue-event-reporter"
        }
        if (configuration.username != null && configuration.password != null) {
            install(Auth) {
                basic {
                    username = configuration.username
                    password = configuration.password
                }
            }
        }
        Charsets {
            register(Charsets.UTF_8)
            sendCharset = Charsets.UTF_8
            responseCharsetFallback = Charsets.UTF_8
        }
        engine {
            followRedirects = true
            customizeRequest {
                setContentCompressionEnabled(true)
            }
            if (configuration.proxy != null) {
                proxy = ProxyBuilder.http(configuration.proxy)
            }
        }
    }

    override fun start() {
        runBlocking {
            try {
                // First verifies of the version is before or after the 7, when the document type support was removed.
                val versionResponse = httpClient.get<String> {
                    url(getElasticsearchUrl())
                }
                val matcher = MAJOR_VERSION_PATTERN.matcher(versionResponse)
                require(matcher.find()) { "Unexpected response body: $versionResponse" }
                val majorVersion = matcher.group(1).toInt()

                val template = if (majorVersion < 7) {
                    jsonMapper.readTree(
                        this::class.java.classLoader.getResource(
                            "events/elasticsearch/index-template-before-7.json")) as ObjectNode
                } else {
                    jsonMapper.readTree(
                        this::class.java.classLoader.getResource(
                            "events/elasticsearch/index-template-from-7.json")) as ObjectNode
                }
                val templateName = "evolue-events"
                template.put("index_patterns", "${configuration.indexPrefix}-*")
                (template["aliases"] as ObjectNode).putObject(configuration.indexPrefix)
                (template["settings"] as ObjectNode).let {
                    it.put("number_of_shards", configuration.shards)
                    it.put("number_of_replicas", configuration.replicas)
                }

                val response = httpClient.put<HttpResponse> {
                    url(getElasticsearchUrl() + "_template/" + templateName)
                    body = TextContent(template.toString(), contentType = ContentType.Application.Json)
                }
                val responseBody: String = response.readText()
                if (responseBody.contains(ERROR_RESPONSE_BODY_SIGNATURE)) {
                    log.error("Failed to create or update the index template {} in Elasticsearch", templateName)
                } else {
                    log.debug("Successfully created or updated the index template {} in Elasticsearch", templateName)
                }
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
        super.start()
    }

    override fun publish() {
        if (buffer.isEmpty()) {
            return
        }

        // Copy the data to export in a local list.
        val eventsToExport = mutableListOf<Event>()
        meterRegistry.timer("events-buffer-extraction", "logger", "elasticsearch").record {
            while (buffer.isNotEmpty() && eventsToExport.size < configuration.batchSize) {
                eventsToExport.add(buffer.pop())
            }
        }

        GlobalScope.launch(publicationContext) {
            val conversionStart = System.nanoTime()
            // Convert the data for a bulk post.
            val requestBody = eventsToExport
                .map { event ->
                    indexFormatter.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.timestamp),
                        Clock.systemUTC().zone)) to writeDocument(event)
                }
                .filter { it.second.isPresent }
                .map {
                    """{ "create" : { "_index" : "${configuration.indexPrefix}-${it.first}", "_type" : "${DOCUMENT_TYPE}", "_id" : "${UUID(
                        random.nextLong(),
                        random.nextLong())}" } }
${it.second.get()}
            """.trimIndent()
                }.joinToString(separator = "\n", postfix = "\n")
            meterRegistry.timer("events-conversion", "logger", "elasticsearch")
                .record(durationSinceNanos(conversionStart))

            val exportStart = System.nanoTime()
            try {
                val responseBody = httpClient.post<String> {
                    url(getElasticsearchUrl() + "_bulk")
                    body = TextContent(requestBody, contentType = ContentType.Application.Json)
                }
                val exportEnd = System.nanoTime()
                val numberOfSentItems: Int = eventsToExport.size

                if (responseBody.contains(ERROR_RESPONSE_BODY_SIGNATURE)) {
                    meterRegistry.timer(EVENTS_EXPORT_TIMER_NAME, "logger", "elasticsearch", "status", "failure")
                        .record(Duration.ofNanos(exportEnd - exportStart))
                    val numberOfCreatedItems = countCreatedItems(responseBody)
                    // TODO Log all the detailed failures as info.
                    log.debug("Failed events payload: {}", requestBody)
                    log.error("Failed to send events to Elasticsearch (sent {} events but only created {}): {}",
                        numberOfSentItems, numberOfCreatedItems, responseBody)
                } else {
                    meterRegistry.timer(EVENTS_EXPORT_TIMER_NAME, "logger", "elasticsearch", "status", "success")
                        .record(Duration.ofNanos(exportEnd - exportStart))
                    log.debug("Successfully sent {} metrics to Elasticsearch", numberOfSentItems)
                }
            } catch (e: Exception) {
                // TODO Reprocess 3 times when an exception is received.
                meterRegistry.timer(EVENTS_EXPORT_TIMER_NAME, "logger", "elasticsearch", "status", "error")
                    .record(durationSinceNanos(exportStart))
                log.error(e.message, e)
            }
        }
    }

    /**
     * Generate a JSON representation of an event.
     *
     * Any type can be used for value, but [Boolean]s, [Number]s, [String]s, [java.time.Duration]s, [java.time.temporal.Temporal]s,
     * [EventGeoPoint]s, [EventRange]s and [Throwable]s are interpreted.
     */
    @VisibleForTesting
    fun writeDocument(event: Event): Optional<String> {
        val timestamp = generateTimestamp(event.timestamp)
        val sb = StringBuilder("{\"@timestamp\":\"").append(timestamp).append('"')
            .append(",\"name\":\"").append(StringEscapeUtils.escapeJson(event.name)).append('"')
            .append(",\"level\":\"").append(event.level.toString().toLowerCase()).append('"')

        if (event.tags.isNotEmpty()) {
            sb.append(",\"tags\":{")
            event.tags.map { tag ->
                "\"${StringEscapeUtils.escapeJson(tag.key)}\":\"${StringEscapeUtils.escapeJson(tag.value)}\""
            }.joinToString(",").let { sb.append(it) }
            sb.append("}")
        }

        event.value?.let { value ->
            when {
                value is String -> {
                    sb.append(",\"message\":\"").append(StringEscapeUtils.escapeJson(value)).append('"')
                }
                value is Boolean -> {
                    sb.append(",\"boolean\":").append(value)
                }
                value is Double && !value.isFinite() -> {
                    sb.append(",\"non-finite-decimal\":\"").append(value.toString()).append('"')
                }
                value is Number -> {
                    sb.append(",\"number\":").append(value)
                }
                value is Instant || value is ZonedDateTime -> {
                    sb.append(",\"instant\":\"").append(TIMESTAMP_FORMATTER.format(value as TemporalAccessor))
                        .append('"')
                }
                value is LocalDateTime -> {
                    sb.append(",\"instant\":\"")
                        .append(TIMESTAMP_FORMATTER.format(value.atZone(ZoneId.systemDefault()))).append('"')
                }
                value is Throwable -> {
                    sb.append(",\"error\":\"").append(StringEscapeUtils.escapeJson(value.message)).append('"')
                    sb.append(",\"stack-trace\":\"").append(StringEscapeUtils.escapeJson(stackTraceToString(value)))
                        .append('"')
                }
                value is Duration -> {
                    if (configuration.durationAsNano) {
                        sb.append(",\"duration\":").append(value.toNanos()).append(",\"unit\":\"nanoseconds\"")
                    } else {
                        sb.append(",\"duration\":").append(value.toMillis()).append(",\"unit\":\"milliseconds\"")
                    }
                }
                value is EventGeoPoint -> {
                    sb.append(",\"latitude\":").append(value.latitude).append(",\"longitude\":").append(value.longitude)
                }
                value is EventRange<*> -> {
                    sb.append(",\"lower-bound\":").append(value.lowerBound).append(",\"upper-bound\":")
                        .append(value.upperBound)
                }
                value is Iterable<*> || value is Array<*> -> {
                    val actualValues = if (value is Iterable<*>) value else (value as Array<*>).toList()
                    sb.append(",\"values\":[")
                        .append(actualValues.map { jsonMapper.writeValueAsString(it.toString()) }.joinToString(","))
                        .append(']')
                }
                else -> {
                    // Convert to JSON and copy the JSON as a raw value.
                    val stringRepresentation = jsonMapper.writeValueAsString(value)
                    sb.append(",\"value\":").append(jsonMapper.writeValueAsString(stringRepresentation))
                }
            }
        }
        sb.append("}")
        return Optional.of(sb.toString())
    }

    /**
     * Generate an ISO formatted date time from the timestamp in milliseconds since epoch.
     */
    private fun generateTimestamp(timestampMilli: Long): String {
        return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(timestampMilli))
    }

    /**
     * Convert the stack trace of a [Throwable] into a [String].
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

    /**
     * Randomly fetches an URL to an Elasticsearch instance.
     */
    private fun getElasticsearchUrl(): String {
        return urls[(urls.size * Math.random()).toInt()]
    }

    /**
     * Proudly pasted from [io.micrometer.elastic.ElasticMeterRegistry.countCreatedItems].
     */
    @VisibleForTesting
    fun countCreatedItems(responseBody: String): Int {
        val matcher = STATUS_CREATED_PATTERN.matcher(responseBody)
        var count = 0
        while (matcher.find()) {
            count++
        }
        return count
    }

    companion object {

        private const val DOCUMENT_TYPE = "_doc"

        private const val EVENTS_EXPORT_TIMER_NAME = "events-export"

        @JvmStatic
        private val MAJOR_VERSION_PATTERN = Pattern.compile("\"number\" *: *\"([\\d]+)")

        @JvmStatic
        private val ERROR_RESPONSE_BODY_SIGNATURE = "\"errors\":true"

        @JvmStatic
        private val STATUS_CREATED_PATTERN = Pattern.compile("\"status\":201")

        @JvmStatic
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT

        @JvmStatic
        private val log = logger()
    }

}