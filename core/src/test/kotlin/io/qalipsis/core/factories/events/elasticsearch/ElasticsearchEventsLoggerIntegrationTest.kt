package io.qalipsis.core.factories.events.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.Charsets
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.qalipsis.api.events.EventGeoPoint
import io.qalipsis.api.events.EventRange
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.PrintWriter
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
@ExperimentalCoroutinesApi
@Testcontainers(disabledWithoutDocker = true)
@Disabled("The concept of event logging has to be reviewed")
internal class ElasticsearchEventsLoggerIntegrationTest {

    // The meter registry should provide a timer that execute the expressions to record.
    private val meterRegistry: MeterRegistry = relaxedMockk {
        every { timer(any(), *anyVararg()) } returns relaxedMockk {
            every { record(any<Runnable>()) } answers { (firstArg() as Runnable).run() }
        }
    }

    @ParameterizedTest(name = "should export data with {0}")
    @MethodSource("provideContainers")
    @Timeout(30)
    internal fun `should export data`(containerAndFormatter: EsContainerContext) {
        val configuration = ElasticsearchEventLoggerConfiguration(
            urls = listOf(containerAndFormatter.url),
            batchSize = 100, lingerPeriod = Duration.ofMinutes(1), publishers = 1
        )
        val logger = ElasticsearchEventsLogger(configuration, meterRegistry)
        logger.start()

        logger.info("my-event")
        logger.info("my-event", null, "key-1" to "value-1", "key-2" to "value-2")

        val instantNow = Instant.now().minusSeconds(12)
        val zdtNow = ZonedDateTime.now()
        val ldtNow = LocalDateTime.now().plusDays(1)
        val values = createTestData(instantNow, zdtNow, ldtNow, containerAndFormatter.dateTimeFormatter)

        val logValues = listOf(*values.keys.toTypedArray())
        logValues.forEachIndexed { index, value ->
            logger.info("my-event-$index", value)
        }
        logger.publish()

        // Wait for the publication to be done.
        Thread.sleep(1000)

        refreshEvents(containerAndFormatter.url)
        Thread.sleep(1000)

        val hitsJson = requestEvents(containerAndFormatter.url)
        val hits = hitsJson.withArray("hits")
        Assertions.assertEquals(logValues.size + 2, hits.size())

        // Verification of the overall values.
        val expectedIndex = "qalipsis-events-${DateTimeFormatter.ofPattern("uuuu-MM-dd").format(zdtNow)}"
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
            ElasticsearchEventsLoggerTest.MyTestObject() to { json ->
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

    private fun refreshEvents(url: String) = runBlockingTest {
        val httpClient = createHttpClient()
        // Force the refresh of the index to make the documents available.
        httpClient.post<String>(url + "/_refresh")
    }

    private fun requestEvents(url: String): ObjectNode {
        val response = runBlocking {
            val httpClient = createHttpClient()
            // Force the refresh of the index to make the documents available.
            httpClient.post<String>(url + "/_refresh")

            httpClient.get<String>(url + "/qalipsis-events-*/_search") {
                body = TextContent(
                    "{\"size\":100,\"query\":{\"bool\":{\"must\":[{\"match_all\":{}}]}},\"stored_fields\":[\"*\"]}",
                    contentType = ContentType.Application.Json
                )
            }
        }

        return ObjectMapper().readTree(response)["hits"] as ObjectNode
    }

    companion object {

        const val ES_6_VERSION = "6.8.3"

        const val ES_7_VERSION = "7.9.3"

        @Container
        @JvmStatic
        val es6 = ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss").withTag(ES_6_VERSION)
        ).withCreateContainerCmdModifier {
            it.hostConfig!!.withMemory(2e30.toLong()).withCpuCount(2)
        } // 2 CPUs and 1GB Max Memory

        @Container
        @JvmStatic
        val es7 = ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss").withTag(ES_7_VERSION)
        ).withCreateContainerCmdModifier {
            it.hostConfig!!.withMemory(2e30.toLong()).withCpuCount(2)
        } // 2 CPUs and 1GB Max Memory

        /**
         * Provides the data for the parameterized test. The first side of the pair is the Docker container
         * to use and the second is the formatter used by the related version of ES to format the dates.
         */
        @JvmStatic
        fun provideContainers() = Stream.of(
            Arguments.of(
                EsContainerContext(
                    ES_6_VERSION,
                    "http://${es6.httpHostAddress}",
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME
                )
            ),
            Arguments.of(
                EsContainerContext(
                    ES_7_VERSION,
                    "http://${es7.httpHostAddress}",
                    DateTimeFormatter.ISO_INSTANT
                )
            )
        )
    }

    class EsContainerContext(
        private val version: String,
        val url: String,
        val dateTimeFormatter: DateTimeFormatter
    ) {
        override fun toString(): String {
            return version
        }
    }
}
