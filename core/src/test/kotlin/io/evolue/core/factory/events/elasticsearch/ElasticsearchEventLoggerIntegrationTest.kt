package io.evolue.core.factory.events.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.evolue.api.events.EventGeoPoint
import io.evolue.api.events.EventRange
import io.evolue.core.factory.eventslogger.elasticsearch.ElasticsearchEventLoggerTest
import io.evolue.test.coroutines.AbstractCoroutinesTest
import io.evolue.test.mockk.relaxedMockk
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.Charsets
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import java.util.stream.Stream

/**
 * Complex integration test with Elasticsearch containers to validate that the bulk indexation is working and the
 * fields are successfully stored.
 *
 * @author Eric Jessé
 */
@Testcontainers(disabledWithoutDocker = true)
internal class ElasticsearchEventLoggerIntegrationTest : AbstractCoroutinesTest() {

    // The meter registry should provide a timer that execute the expressions to record.
    private val meterRegistry: MeterRegistry = relaxedMockk {
        every { timer(any(), *anyVararg()) } returns relaxedMockk {
            every { record(any<Runnable>()) } answers { (firstArg() as Runnable).run() }
        }
    }

    @ParameterizedTest
    @MethodSource("provideContainers")
    internal fun `should export data`(containerAndFormatter: Pair<ElasticsearchContainer, DateTimeFormatter>) {

        val configuration = ElasticsearchEventLoggerConfiguration(
            urls = listOf(containerAndFormatter.first.url),
            batchSize = 100, lingerPeriod = Duration.ofMinutes(1), publishers = 1
        )
        val logger = ElasticsearchEventLogger(configuration, meterRegistry)
        logger.start()

        logger.info("my-event")
        logger.info("my-event", null, "key-1" to "value-1", "key-2" to "value-2")

        val instantNow = Instant.now().minusSeconds(12)
        val zdtNow = ZonedDateTime.now()
        val ldtNow = LocalDateTime.now().plusDays(1)
        val values = createTestData(instantNow, zdtNow, ldtNow, containerAndFormatter.second)

        val logValues = listOf(*values.keys.toTypedArray())
        logValues.forEachIndexed { index, value ->
            logger.info("my-event-$index", value)
        }
        logger.publish()

        // Wait for the publication to be done.
        Thread.sleep(1000)

        val hitsJson = requestEvents(containerAndFormatter.first)
        val hits = hitsJson.withArray("hits")
        Assertions.assertEquals(logValues.size + 2, hits.size())

        // Verification of the overall values.
        val expectedIndex = "evolue-events-${DateTimeFormatter.ofPattern("uuuu-MM-dd").format(zdtNow)}"
        hits.forEach { hit ->
            Assertions.assertEquals(expectedIndex, hit["_index"].asText())
            (hit["fields"] as ObjectNode).apply {
                Assertions.assertEquals("info", this.withArray("level")[0].asText())
                Assertions.assertNotNull(this.withArray("@timestamp")[0].asText())
            }
        }

        // Verification of the events without values but with tags.
        assertDoesNotThrow("Item with tags should be found") {
            hits.first { item ->
                kotlin.runCatching {
                    (item["fields"] as ObjectNode).withArray("tags.key-1")[0].asText() == "value-1"
                            && (item["fields"] as ObjectNode).withArray("tags.key-2")[0].asText() == "value-2"
                }.getOrElse { _ -> false }
            }
        }

        // Verification of the events with values.
        logValues.forEachIndexed { index, value ->
            val searchCriteria = values.getValue(value)
            assertDoesNotThrow("Item of value ${value} and type ${value::class} was not found") {
                hits.first { item ->
                    kotlin.runCatching {
                        (item["fields"] as ObjectNode).let { fields ->
                            "my-event-$index" == fields.withArray("name")[0].asText() && searchCriteria(fields)
                        }
                    }.getOrElse { _ -> false }
                }
            }

        }
    }

    /**
     * Create the test data set with the value to log as key and the condition to match when asserting the data as value.
     */
    private fun createTestData(
        instantNow: Instant, zdtNow: ZonedDateTime,
        ldtNow: LocalDateTime,
        formatter: DateTimeFormatter
    ): Map<Any, (json: ObjectNode) -> Boolean> {
        val values = mapOf<Any, ((json: ObjectNode) -> Boolean)>(
            "my-message" to { json -> json.withArray("message")[0].asText() == "my-message" },
            true to { json -> json.withArray("boolean")[0].asBoolean() },
            123.65 to { json -> json.withArray("number")[0].asDouble() == 123.65 },
            Double.POSITIVE_INFINITY to { json ->
                json.withArray("non-finite-decimal")[0].asText() == "Infinity"
            },
            Double.NEGATIVE_INFINITY to { json ->
                json.withArray("non-finite-decimal")[0].asText() == "-Infinity"
            },
            Double.NaN to { json ->
                json.withArray("non-finite-decimal")[0].asText() == "NaN"
            },
            123.65.toFloat() to { json -> json.withArray("number")[0].asDouble() == 123.65 },
            123.65.toBigDecimal() to { json -> json.withArray("number")[0].asDouble() == 123.65 },
            123 to { json -> json.withArray("number")[0].asInt() == 123 },
            123.toBigInteger() to { json -> json.withArray("number")[0].asInt() == 123 },
            123.toLong() to { json -> json.withArray("number")[0].asInt() == 123 },
            instantNow to { json ->
                formatter.parse(
                    json.withArray("instant")[0].asText()
                ) { temporal: TemporalAccessor ->
                    Instant.from(temporal)
                } == instantNow.truncatedTo(ChronoUnit.MILLIS)
            },
            zdtNow to { json ->
                formatter.parse(
                    json.withArray("instant")[0].asText()
                ) { temporal: TemporalAccessor ->
                    Instant.from(temporal)
                } == zdtNow.toInstant().truncatedTo(ChronoUnit.MILLIS)
            },
            ldtNow to { json ->
                formatter.parse(
                    json.withArray("instant")[0].asText()
                ) { temporal: TemporalAccessor ->
                    Instant.from(temporal)
                } == ldtNow.atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.MILLIS)
            },
            relaxedMockk<Throwable> {
                every { message } returns "my-error"
                every { printStackTrace(any<PrintWriter>()) } answers {
                    (firstArg() as PrintWriter).write("this is the stack")
                }
            } to { json ->
                json.withArray("error")[0].asText() == "my-error" && json.withArray(
                    "stack-trace"
                )[0].asText() == "this is the stack"
            },
            Duration.ofSeconds(12) to { json ->
                json.withArray("duration")[0].asInt() == 12000 && json.withArray(
                    "unit"
                )[0].asText() == "milliseconds"
            },
            EventGeoPoint(12.34, 34.76) to { json ->
                json.withArray("latitude")[0].asDouble() == 12.34 && json.withArray(
                    "longitude"
                )[0].asDouble() == 34.76
            },
            EventRange(12.34, 34.76) to { json ->
                json.withArray("lower-bound")[0].asDouble() == 12.34 && json.withArray(
                    "upper-bound"
                )[0].asDouble() == 34.76
            },
            arrayOf(12.34, "here is the test") to { json ->
                json.withArray("values").let {
                    it[0].asText() == "12.34" && it[1].asText() == "here is the test"
                }
            },
            listOf(12.34, "here is the test") to { json ->
                json.withArray("values").let {
                    it[0].asText() == "12.34" && it[1].asText() == "here is the test"
                }
            },
            ElasticsearchEventLoggerTest.MyTestObject() to { json ->
                json.withArray("value")[0].asText() == "{\"property1\":1243.65,\"property2\":\"here is the test\"}"
            }
        )
        return values
    }

    private fun createHttpClient(): HttpClient {
        return HttpClient(Apache) {
            Charsets {
                register(Charsets.UTF_8)
                sendCharset = Charsets.UTF_8
                responseCharsetFallback = Charsets.UTF_8
            }
            engine {
                followRedirects = true
            }
        }
    }

    private fun requestEvents(container: ElasticsearchContainer): ObjectNode {
        val response = runBlocking {
            val httpClient = createHttpClient()
            // Force the refresh of the index to make the documents available.
            httpClient.post<String>(container.url + "/evolue-events-*/_refresh")

            httpClient.get<String>(container.url + "/evolue-events-*/_search") {
                body = TextContent(
                    "{\"size\":100,\"query\":{\"bool\":{\"must\":[{\"match_all\":{}}]}},\"stored_fields\":[\"*\"]}",
                    contentType = ContentType.Application.Json
                )
            }
        }

        return ObjectMapper().readTree(response)["hits"] as ObjectNode
    }

    class ElasticsearchContainer constructor(version: String) :
        GenericContainer<ElasticsearchContainer>("docker.elastic.co/elasticsearch/elasticsearch:$version") {

        val url: String
            get() = "http://${getContainerIpAddress()}:${getMappedPort(PORT)}"

        override fun configure() {
            addExposedPorts(PORT)
            withEnv("discovery.type", "single-node")
            waitingFor(
                HttpWaitStrategy()
                    .forPort(9200)
                    .forStatusCode(HttpURLConnection.HTTP_OK)
                    .withStartupTimeout(Duration.ofMinutes(3))
            )
        }

        companion object {
            private const val PORT = 9200
        }
    }

    companion object {

        @Container
        @JvmStatic
        val es6 = ElasticsearchContainer("6.5.4")

        @Container
        @JvmStatic
        val es7 = ElasticsearchContainer("7.6.2")

        /**
         * Provide the data for the parameterized test. The first side of the pair is the Docker container
         * to use and the second is the formatter used by the related version of ES to format the dates.
         */
        @JvmStatic
        fun provideContainers() =
            Stream.of(es6 to DateTimeFormatter.ISO_OFFSET_DATE_TIME, es7 to DateTimeFormatter.ISO_INSTANT)
    }
}